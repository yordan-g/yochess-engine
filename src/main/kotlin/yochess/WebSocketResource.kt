package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import mu.KotlinLogging
import yochess.dtos.Message
import yochess.dtos.MessageEnDecoder
import yochess.dtos.Move
import yochess.services.GamesManager
import yochess.services.MoveService
import yochess.services.toXY

@ApplicationScoped
@ServerEndpoint(
    "/chess/{username}/{gameId}",
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
        @PathParam("username") username: String,
        @PathParam("gameId") existingGameId: String?
    ) {
        gamesService.addPlayerToGame(session)
    }

    @OnClose
    fun onClose(
        session: Session,
        @PathParam("username") username: String,
        @PathParam("gameId") gameId: String
    ) {
        gamesService.closeGame(username, session)
    }

    @OnError
    fun onError(
        session: Session,
        @PathParam("username") username: String,
        @PathParam("gameId") gameId: String,
        throwable: Throwable
    ) {
        logger.error("Error Received ... $throwable")

        gamesService.closeGame(gameId, session)
    }

    @OnMessage
    fun onMessage(
        moveRequest: Message,
        @PathParam("username") username: String
    ) {
        if (moveRequest is Move) {
            val moveResult = moveService.processMove(
                gameState = gamesService.getGame(moveRequest.gameId).state,
                from = moveRequest.squareFrom.toXY(),
                to = moveRequest.squareTo.toXY(),
                moveRequest = moveRequest
            )
            gamesService.broadcastMove(
                moveResult
            )
        }
    }
}