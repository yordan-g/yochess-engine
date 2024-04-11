package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.HandshakeRequest
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import jakarta.websocket.server.ServerEndpointConfig
import jakarta.ws.rs.WebApplicationException
import mu.KotlinLogging
import yochess.dtos.*
import yochess.services.*

@ApplicationScoped
@ServerEndpoint(
    "/chess/{userId}",
    encoders = [MessageEnDecoder::class],
    decoders = [MessageEnDecoder::class],
//    configurator = CustomConfigurator::class
)
class WebSocketResource(
    private val moveService: MoveService,
    private val gamesService: GamesManager
) {
    private val logger = KotlinLogging.logger {}

    @OnOpen
    fun onOpen(
        session: Session,
        @PathParam("userId") userId: String
    ) {
        val rematchGameId = session.requestParameterMap["rematchGameId"]?.firstOrNull()

        val customGameId = session.requestParameterMap["customGameId"]?.firstOrNull()
        val isCreator = session.requestParameterMap["isCreator"]?.firstOrNull()

        logger.info { "rematchGameId -- $rematchGameId | customGameId -- $customGameId | isCreator -- $isCreator" }

        when {
            rematchGameId != null -> {
                gamesService.connectToRematchGame(rematchGameId, userId, session)
            }

            customGameId != null -> {
                gamesService.connectToCustomGame(customGameId, isCreator, userId, session)
            }

            else -> gamesService.connectToRandomGame(userId, session)
        }
    }

    @OnMessage
    fun onMessage(
        incomingMessage: Message,
        @PathParam("userId") userId: String
    ) {
        when (incomingMessage) {
            is Init -> {}
            is CommunicationError -> {}
            is Move -> {
                val (moveResult: Move, endResult: End?) = moveService.processMove(
                    gameState = gamesService.getGame(incomingMessage.gameId).state,
                    from = incomingMessage.squareFrom.toXY(),
                    to = incomingMessage.squareTo.toXY(),
                    moveRequest = incomingMessage
                )

                gamesService.broadcast(moveResult)
                endResult?.let { gamesService.broadcast(endResult) }
            }

            is End -> {
                when {
                    incomingMessage.leftGame == true -> {
                        gamesService.closeGame(incomingMessage)
                    }

                    incomingMessage.close == true -> {
                        gamesService.closeGame(incomingMessage)
                    }

                    incomingMessage.rematch == true -> {
                        logger.info { "Received rematch message ---" }

                        gamesService.offerRematch(incomingMessage.gameId, userId)
                    }

                    incomingMessage.timeout == true -> {
                        gamesService.endGame(incomingMessage, userId)
                        gamesService.broadcast(incomingMessage)
                    }

                    else -> gamesService.broadcast(incomingMessage)
                }
            }

            is ChangeName -> {
                logger.info { "Received ChangeName message for userId: $userId, message: $incomingMessage" }

                gamesService.changePlayerName(userId, incomingMessage)
            }
        }
    }

    @OnClose
    fun onClose(
        session: Session,
        @PathParam("userId") userId: String
    ) {
        logger.info { "Session closed --------------" }
    }

    @OnError
    fun onError(
        session: Session,
        @PathParam("userId") userId: String,
        throwable: Throwable
    ) {
        logger.error { "Connection Issue | ${throwable.message}" }
        logger.error { "Connection Issue | ${throwable.localizedMessage}" }
        logger.error { "Connection Issue | ${throwable.cause}" }

        when (throwable) {
            is GameNotFound -> {
                session.asyncRemote.sendObject(
                    CommunicationError(userMessage = "Your game has ended unexpectedly. You can report a problem here or try another game from the 'Play' button!")
                )
                session.close()
            }

            is BadCustomGameRequest -> {
                session.asyncRemote.sendObject(
                    CommunicationError(userMessage = "The game room doesn't exist. Please check with you friend or start another game!")
                )
                session.close()
            }

            is InvalidGameState -> {
                // todo: determine if this causes issues for users
                session.close()
            }

            else -> {
                session.close()
            }
        }
        // if error use "session"
        // 1. send a message to the client saying what happened
        //      may search game by session so that we can notify both players
        // 2. session.close()
    }
}

//class CustomConfigurator : ServerEndpointConfig.Configurator() {
//    override fun modifyHandshake(sec: ServerEndpointConfig?, request: HandshakeRequest?, response: HandshakeResponse?) {
////        throw WebApplicationException()
//        super.modifyHandshake(sec, request, response)
//    }
//}