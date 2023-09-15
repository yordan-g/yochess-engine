package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import org.jboss.logging.Logger
import yochess.dtos.Init
import yochess.dtos.Move
import java.util.concurrent.*

@ApplicationScoped
@ServerEndpoint(
    "/chess/{username}",
    encoders = [MessageEnDecoder::class, InitEnDecoder::class],
    decoders = [MessageEnDecoder::class, InitEnDecoder::class]
)
class WebSocket(
    private val moveService: MoveService
) {
    private val logger: Logger = Logger.getLogger(this::class.java)

    private val sessions: MutableMap<String, Session> = ConcurrentHashMap<String, Session>()

    @OnOpen
    fun onOpen(session: Session, @PathParam("username") username: String) {
        if (sessions.size < 2) {
            logger.info("Initiating Connection: $username")

            if (sessions.isEmpty()) {
                session.asyncRemote.sendObject(Init(color = "w"))
            } else {
                session.asyncRemote.sendObject(Init(color = "b"))
            }
            sessions[username] = session
        }
    }

    @OnClose
    fun onClose(session: Session?, @PathParam("username") username: String) {
        logger.info("Closing Connection ...")
        sessions.remove(username)
    }

    @OnError
    fun onError(session: Session, @PathParam("username") username: String, throwable: Throwable) {
        logger.info("Error Received ...")
        logger.info(throwable)
        sessions.remove(username)
        broadcast(Move("User $username left on error: $throwable"))
    }

    @OnMessage
    fun onMessage(move: Move, @PathParam("username") username: String) {
        logger.info("Message Received: $move")
        broadcast(
            Move(
                move.piece,
                move.squareFrom,
                move.squareTo,
                true
            )
        )
    }

    private fun broadcast(move: Move?) {
        sessions.values.forEach { session: Session ->
            session.asyncRemote.sendObject(move) { result ->
                if (result.exception != null) {
                    logger.error("Unable to send message: " + result.exception)
                }
            }
        }
    }
}