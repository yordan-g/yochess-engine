package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import mu.KotlinLogging
import yochess.dtos.*
import yochess.services.GamesManager
import yochess.services.MoveService
import yochess.services.toXY

@ApplicationScoped
@ServerEndpoint(
    "/chess/{userId}",
    encoders = [MessageEnDecoder::class],
    decoders = [MessageEnDecoder::class]
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
        val queryParams = session.requestParameterMap
        val rematchGameId = queryParams["rematchGameId"]?.firstOrNull()
        logger.info { "rematchGameId -- $rematchGameId" }

        when {
            rematchGameId != null -> {
                gamesService.connectToRematchGame(rematchGameId, userId, session)
            }
            else -> gamesService.connectToRandomGame(userId, session)
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
        // if error use "session"
        // 1. send a message to the client saying what happened
        //      may search game by session so that we can notify both players
        // 2. session.close()

        logger.error { "Error Received ... $throwable" }
    }

    @OnMessage
    fun onMessage(
        message: Message,
        @PathParam("userId") userId: String
    ) {
        when (message) {
            is Move -> {
                val moveResult = moveService.processMove(
                    gameState = gamesService.getGame(message.gameId).state,
                    from = message.squareFrom.toXY(),
                    to = message.squareTo.toXY(),
                    moveRequest = message
                )
                gamesService.broadcast(moveResult)
            }

            is End -> {
                when {
                    message.leftGame == true -> {
                        gamesService.broadcast(message)
                        gamesService.closeGame(message.gameId)
                    }

                    message.close == true -> {
                        gamesService.broadcast(message)
                        gamesService.closeGame(message.gameId)
                    }

                    message.rematch == true -> {
                        logger.info { "Received rematch message ---" }

                        gamesService.offerRematch(message.gameId, userId)
                    }

                    else -> gamesService.broadcast(message)
                }
            }

            is Init -> {}
            is ChangeName -> {
                logger.info { "Received ChangeName message for userId: $userId, message: $message" }

                gamesService.changePlayerName(userId, message)
            }
        }
    }
}