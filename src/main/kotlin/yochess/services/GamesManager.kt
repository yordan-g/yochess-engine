package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import jakarta.ws.rs.NotFoundException
import mu.KotlinLogging
import yochess.dtos.ChangeName
import yochess.dtos.End
import yochess.dtos.Init
import yochess.dtos.Move
import java.util.*
import java.util.concurrent.*

interface GamesManager {
    fun connectToRandomGame(userId: String, session: Session)
    fun connectToRematchGame(rematchGameId: String, userId: String, session: Session)
    fun broadcast(moveResult: Move)
    fun broadcast(endMessage: End)
    fun closeGame(endMessage: End)
    fun getGame(gameId: String): Game
    fun offerRematch(gameId: String, userId: String)
    fun changePlayerName(userId: String, changeNameMessage: ChangeName)
    fun removeGame(gameId: String)
}

data class GameNotFound(val gameId: String, override val message: String) : RuntimeException(message)
data class InvalidGameState(override val message: String) : RuntimeException(message)

@ApplicationScoped
class DefaultGamesService : GamesManager {
    private val logger = KotlinLogging.logger {}
    private val waitingPlayers: ConcurrentLinkedQueue<Player> = ConcurrentLinkedQueue()
    private val activeGames: ConcurrentHashMap<String, Game> = ConcurrentHashMap()

    override fun removeGame(gameId: String) {
        activeGames.remove(gameId)
    }

    override fun connectToRandomGame(userId: String, session: Session) {
        logger.info { "Start | User: $userId connecting to a random game." }

        when (val matchedPlayer = waitingPlayers.poll()) {
            null -> "Waiting for game".also {
                val player1 = Player(userId = userId, session = session, color = Color.W)
                waitingPlayers.offer(player1)
                player1.session?.asyncRemote?.sendObject(Init(gameId = it))
            }

            else -> UUID.randomUUID().toString().also { newGameId ->
                val player2 = Player(userId = userId, session = session, color = Color.B)
                activeGames[newGameId] = Game(player2, matchedPlayer)
                matchedPlayer.session?.asyncRemote?.sendObject(Init(type = GamePhase.START, color = matchedPlayer.color.lowercase(), gameId = newGameId))
                player2.session?.asyncRemote?.sendObject(Init(type = GamePhase.START, color = player2.color.lowercase(), gameId = newGameId))
            }
        }.also {
            logger.info { "End | User: $userId connected to game: $it" }
            logger.info { "waitingPlayers: ${waitingPlayers.size}" }
            logger.info { "activeGames: ${activeGames.size}" }
        }
    }

    override fun connectToRematchGame(rematchGameId: String, userId: String, session: Session) {
        logger.info { "Start | Adding User: $userId to a Rematch game: $rematchGameId." }

        val game = activeGames[rematchGameId] ?: throw GameNotFound(rematchGameId, "End | Tried to add player a rematch but the game is not found in activeGames!")
        when {
            game.player1.session == null -> {
                game.player1.userId = userId
                game.player1.session = session
                game.player1.session?.asyncRemote?.sendObject(Init(gameId = rematchGameId))
            }

            game.player2.session == null -> {
                game.player2.userId = userId
                game.player2.session = session
                game.player1.session?.asyncRemote?.sendObject(Init(type = GamePhase.START, color = game.player1.color.lowercase(), gameId = rematchGameId))
                game.player2.session?.asyncRemote?.sendObject(Init(type = GamePhase.START, color = game.player2.color.lowercase(), gameId = rematchGameId))
            }

            else -> {
                throw InvalidGameState("End | State problem, both players have opened sessions, the game should have started!").also {
                    logger.error { it.message }
                }
            }
        }
    }

    override fun closeGame(endMessage: End) {
        val gameId = endMessage.gameId
        logger.info("Start | Closing Connection for gameId: ${gameId}")

        broadcast(endMessage)
        // could do a security check if the session matches a session in the active game that needs to be closed
        val removedGame = activeGames.remove(gameId)
            ?: throw GameNotFound(gameId, "Trying to close a game: $gameId but it's not found!").also {
                logger.error { it.message }
            }
        waitingPlayers.remove(removedGame.player1)
        waitingPlayers.remove(removedGame.player2)

        if (removedGame.player1.session?.isOpen == true) {
            removedGame.player1.session?.close()
        }
        if (removedGame.player2.session?.isOpen == true) {
            removedGame.player2.session?.close()
        }

        activeGames.forEach { entry ->
            logger.info("has key: ${entry.key}")
        }
    }

    override fun getGame(gameId: String): Game = activeGames[gameId] ?: throw GameNotFound(gameId, "Cannot find active game: $gameId")

    /** Puts a new Game object in the map so that connectToRematchGame() is able to put users in the Game.
     *  Also switches the colors of players */
    override fun offerRematch(gameId: String, userId: String) {
        logger.info { "Start | Player: $userId is offering a rematch." }

        val currentGame = activeGames[gameId]
        when {
            (currentGame == null || currentGame.player1 == null || currentGame.player2 == null) -> {
                logger.error { "End | There is no such game or player!" }
                return
            }

            currentGame.player1.userId == userId -> currentGame.player1.offeredRematch = true
            currentGame.player2.userId == userId -> currentGame.player2.offeredRematch = true
            else -> {
                logger.error { " End | User: $userId, tried offering a rematch in Game: $gameId but he is not in this game! State error!" }
                return
            }
        }

        if (currentGame.player1.offeredRematch && currentGame.player2.offeredRematch) {
            val rematchGameId = UUID.randomUUID().toString()
            activeGames[rematchGameId] = Game(
                player1 = Player(color = currentGame.player2.color),
                player2 = Player(color = currentGame.player1.color),
            )
            currentGame.player1.session?.asyncRemote?.sendObject(End(gameId = gameId, rematchSuccess = true, rematchGameId = rematchGameId))
            currentGame.player2.session?.asyncRemote?.sendObject(End(gameId = gameId, rematchSuccess = true, rematchGameId = rematchGameId))

            logger.info { "End | Rematch accepted, starting a new game: $rematchGameId" }
        }
    }

    override fun changePlayerName(userId: String, changeNameMessage: ChangeName) {
        val game = activeGames[changeNameMessage.gameId]
            ?: throw GameNotFound(gameId = changeNameMessage.gameId, "Cannot find active game with id: ${changeNameMessage.gameId}")

        when {
            game.player1.userId == userId -> {
                game.player1.username = changeNameMessage.name
                game.player2.session?.asyncRemote?.sendObject(changeNameMessage)
            }

            game.player2.userId == userId -> {
                game.player2.username = changeNameMessage.name
                game.player1.session?.asyncRemote?.sendObject(changeNameMessage)
            }
        }
    }

    override fun broadcast(moveResult: Move) {
        logger.debug { "Sending | $moveResult" }

        activeGames[moveResult.gameId]?.let {
            it.player1?.session?.asyncRemote?.sendObject(moveResult)
            it.player2?.session?.asyncRemote?.sendObject(moveResult)
        }
    }

    override fun broadcast(endMessage: End) {
        logger.info { "Sending | $endMessage" }

        activeGames[endMessage.gameId]?.let {
            it.player1?.session?.asyncRemote?.sendObject(endMessage)
            it.player2?.session?.asyncRemote?.sendObject(endMessage)
        }
    }
}

enum class GamePhase { INIT, START }

data class Game(
    val player1: Player,
    val player2: Player,
) {
    val state: GameState = GameState()
}

data class Player(
    var session: Session? = null,
    var userId: String? = null,
    var username: String? = null,
    var color: Color,
    var offeredRematch: Boolean = false
)
