package yochess

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import org.jboss.logging.Logger
import java.util.concurrent.*
import java.util.function.Consumer

@ApplicationScoped
@ServerEndpoint(
    "/chess/{username}",
    encoders = [MessageEnDecoder::class],
    decoders = [MessageEnDecoder::class]
)
class WebSocket {
    private val logger: Logger = Logger.getLogger(this::class.java)

    private val sessions: MutableMap<String, Session> = ConcurrentHashMap<String, Session>()

    @OnOpen
    fun onOpen(session: Session, @PathParam("username") username: String) {
        logger.info("Initiating Connection: $username")
        sessions[username] = session
    }

    @OnClose
    fun onClose(session: Session?, @PathParam("username") username: String) {
        logger.info("Closing Connection ...")
        sessions.remove(username)
        broadcast(MoveRequest("User $username left"))
    }

    @OnError
    fun onError(session: Session, @PathParam("username") username: String, throwable: Throwable) {
        logger.info("Error Received ...")
        logger.info(throwable)
        sessions.remove(username)
        broadcast(MoveRequest("User $username left on error: $throwable"))
    }

    @OnMessage
    fun onMessage(moveRequest: MoveRequest, @PathParam("username") username: String) {
        logger.info("Message Received: $moveRequest")
        broadcast(
            MoveRequest(
                moveRequest.piece,
                moveRequest.squareFrom,
                moveRequest.squareTo,
                true
            )
        )
    }

    private fun broadcast(moveRequest: MoveRequest?) {
        sessions.values.forEach(Consumer<Session> { s: Session ->
            s.getAsyncRemote().sendObject(moveRequest) { result ->
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException())
                }
            }
        })
    }
}