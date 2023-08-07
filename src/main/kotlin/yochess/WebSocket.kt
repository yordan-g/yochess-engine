package yochess

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
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
//        broadcast(MoveRequest("User $username left"))
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
        if (moveRequest.piece == "z") {
            sessions.values.forEach { s -> s.close() }
            return
        }

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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Init(
    @JsonProperty("color")
    val color: String,
)