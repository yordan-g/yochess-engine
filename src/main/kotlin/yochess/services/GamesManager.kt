package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import jakarta.ws.rs.NotFoundException
import org.jboss.logging.Logger
import yochess.dtos.InitMessage
import yochess.dtos.Move
import java.util.*
import java.util.concurrent.*

interface GamesManager {
    fun addPlayerToGame(player: Session): String
    fun broadcastMove(move: Move)
    fun closeGame(id: String, session: Session)
    fun getGame(gameId: String): Game
}

@ApplicationScoped
class DefaultGamesService : GamesManager {
    private val logger: Logger = Logger.getLogger(this::class.java)
    private val waitingPlayers: ConcurrentLinkedQueue<Session> = ConcurrentLinkedQueue()
    private val activeGames: MutableMap<String, Game> = ConcurrentHashMap()

    override fun addPlayerToGame(player: Session): String = when (val matchedPlayer = waitingPlayers.poll()) {
        null -> "Waiting for game".also {
            waitingPlayers.offer(player)
            player.asyncRemote.sendObject(InitMessage(gameId = it))
        }

        else -> UUID.randomUUID().toString().also {
            activeGames[it] = Game(player, matchedPlayer)
            matchedPlayer.asyncRemote.sendObject(InitMessage(type = WebSocketPhase.START, color = "w", gameId = it))
            player.asyncRemote.sendObject(InitMessage(type = WebSocketPhase.START, color = "b", gameId = it))
        }
    }.also {
        logger.info("Player connected to game: $it")
    }

    override fun closeGame(id: String, session: Session) {
        // could do a security check if the session matches a session in the active game that needs to be closed
        activeGames.remove(id)
        waitingPlayers.remove(session)
        logger.info("Closing Connection ... gameId: $id")
    }

    override fun getGame(gameId: String): Game {
        return activeGames[gameId] ?: throw NotFoundException("error")
    }

    override fun broadcastMove(moveResult: Move) {
        logger.debug("Message Received: $moveResult, valid: ${moveResult.valid}")

        activeGames[moveResult.gameId]?.let {
            it.player1.asyncRemote.sendObject(moveResult)
            it.player2.asyncRemote.sendObject(moveResult)
        }
    }
}

enum class WebSocketPhase {
    INIT,
    START
}

data class Game(
    var player1: Session,
    var player2: Session,
) {
    val state: GameState = GameState()
}
