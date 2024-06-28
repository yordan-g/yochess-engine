package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint

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
        logger.info { "Open session for User($userId) | Start" }

        val rematchGameId = session.requestParameterMap["rematchGameId"]?.firstOrNull()
        val customGameId = session.requestParameterMap["customGameId"]?.firstOrNull()
        val isCreator = session.requestParameterMap["isCreator"]?.firstOrNull()

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
                        gamesService.closeGame(incomingMessage, userId)
                    }

                    incomingMessage.close == true -> {
                        gamesService.closeGame(incomingMessage, userId)
                    }

                    incomingMessage.rematch == true -> {
                        gamesService.offerRematch(incomingMessage.gameId, userId)
                    }

                    incomingMessage.timeout == true -> {
                        gamesService.endGame(incomingMessage, userId)
                        gamesService.broadcast(incomingMessage)
                    }

                    else -> gamesService.broadcast(incomingMessage)
                }
            }

            is Draw -> {
                when {
                    incomingMessage.offerDraw -> {
                        gamesService.offerDraw(incomingMessage.gameId, userId)
                    }
                    incomingMessage.denyDraw -> {
                        gamesService.denyDrawOffer(incomingMessage.gameId, userId)
                    }
                }

            }

            is ChangeName -> {
                gamesService.changePlayerName(userId, incomingMessage)
            }
        }
    }

    @OnClose
    fun onClose(
        session: Session,
        @PathParam("userId") userId: String
    ) {
        logger.info { "Closing Session for User($userId) | Start" }

        gamesService.closeGameUponClientSessionEnd(userId)
    }

    @OnError
    fun onError(
        session: Session,
        @PathParam("userId") userId: String,
        throwable: Throwable
    ) {
        logger.error { "Connection Issue User($userId) err: $throwable" }

        when (throwable) {
            is GameNotFound -> {
                session.asyncRemote.sendObject(
                    CommunicationError(userMessage = "Your game has ended unexpectedly. You can report a problem here or try another game from the 'Play' button!")
                )
            }

            is BadCustomGameRequest -> {
                session.asyncRemote.sendObject(
                    CommunicationError(userMessage = "The game room doesn't exist. Please check with you friend or start another game!")
                )
            }
            is InvalidGameState -> {}
            else -> {}
        }
    }
}

/** Potential config for security
class CustomConfigurator : ServerEndpointConfig.Configurator() {
    override fun modifyHandshake(sec: ServerEndpointConfig?, request: HandshakeRequest?, response: HandshakeResponse?) {
//        throw WebApplicationException()
        super.modifyHandshake(sec, request, response)
    }
}
*/
