package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import org.jboss.logging.Logger
import yochess.dtos.InitMessage
import yochess.dtos.Move
import java.util.*
import java.util.concurrent.*

@ApplicationScoped
class MoveService {
}

interface GamesManager {
    fun addPlayerToGame(player: Session): InitMessage
    fun broadcastMove(move: Move, username: String)
    fun closeGame(id: String, session: Session)
}

@ApplicationScoped
class DefaultGamesService : GamesManager {
    private val logger: Logger = Logger.getLogger(this::class.java)
    private val waitingPlayers: ConcurrentLinkedQueue<Session> = ConcurrentLinkedQueue()
    private val activeGames: MutableMap<String, Game> = ConcurrentHashMap()

    override fun addPlayerToGame(player: Session): InitMessage = when (val matchedPlayer = waitingPlayers.poll()) {
        null -> InitMessage(color = "w", gameId = "gameId").also {
            waitingPlayers.offer(player)
            player.asyncRemote.sendObject(it)
        }

        else -> InitMessage(color = "b", gameId = UUID.randomUUID().toString()).also {
            activeGames[it.gameId] = Game(player, matchedPlayer)
            matchedPlayer.asyncRemote.sendObject(it)
            player.asyncRemote.sendObject(it)
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

    override fun broadcastMove(move: Move, username: String) {
        logger.info("Message Received: $move")

        arrayOf(activeGames[move.gameId]?.player1, activeGames[move.gameId]?.player2).forEach {
            it?.asyncRemote?.sendObject(move) { result ->
                if (result.exception != null) {
                    logger.error("Unable to send message: " + result.exception)
                }
            }
        }
    }
}

data class Game(var player1: Session, var player2: Session? = null)
