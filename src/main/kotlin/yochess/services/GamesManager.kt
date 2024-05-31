package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import mu.KotlinLogging
import yochess.dtos.*
import java.util.*
import java.util.concurrent.*

interface GamesManager {
    fun connectToRandomGame(userId: String, session: Session)
    fun connectToRematchGame(rematchGameId: String, userId: String, session: Session)
    fun connectToCustomGame(customGameId: String, isCreator: String?, userId: String, session: Session)
    fun broadcast(message: Message)
    fun closeGame(endMessage: End)
    fun getGame(gameId: String): Game
    fun offerRematch(gameId: String, userId: String)
    fun changePlayerName(userId: String, changeNameMessage: ChangeName)
    fun endGame(message: End, userId: String)
    fun getWaitingPlayers(): LinkedList<Player>
    fun getActiveGames(): HashMap<String, Game>
    fun closeGameUponClientSessionEnd(userId: String)
}

data class GameNotFound(val gameId: String, override val message: String) : RuntimeException(message)
data class InvalidGameState(override val message: String) : RuntimeException(message)
data class BadCustomGameRequest(override val message: String) : RuntimeException(message)

@ApplicationScoped
class DefaultGamesService : GamesManager {
    private val logger = KotlinLogging.logger {}
    private val waitingPlayers: ConcurrentLinkedQueue<Player> = ConcurrentLinkedQueue()
    private val activeGames: ConcurrentHashMap<String, Game> = ConcurrentHashMap()

    override fun connectToRandomGame(userId: String, session: Session) {
        logger.info { "User($userId) connecting to Game | Start" }

        when (val matchedPlayer = waitingPlayers.poll()) {
            null -> {
                val newGameId = UUID.randomUUID().toString()
                val player1 = Player(userId = userId, color = Color.W).apply { this.session = session }
                activeGames[newGameId] = Game(player1, Player(color = Color.B))
                waitingPlayers.offer(player1)

                player1.session.asyncRemote.sendObject(Init(gameId = newGameId, color = player1.color.lowercase()))
            }

            else -> {
                val (gameId, game) = activeGames.filter { entry -> entry.value.player1.userId == matchedPlayer.userId }
                    .map { Pair(it.key, it.value) }
                    .firstOrNull()
                    ?: throw InvalidGameState("A second player tries do connect to a game but the game can't be found in the Map!")

                game.player2.userId = userId
                game.player2.session = session
                game.player1.session.asyncRemote.sendObject(Init(type = GamePhase.START, gameId = gameId, color = game.player1.color.lowercase()))
                game.player2.session.asyncRemote.sendObject(Init(type = GamePhase.START, gameId = gameId, color = game.player2.color.lowercase()))
            }
        }.also {
            logger.info { "User($userId) connected to game | Exit" }
            logger.info { "waitingPlayers=${waitingPlayers.size} activeGames=${activeGames.size} | Exit" }
        }
    }

    override fun connectToRematchGame(rematchGameId: String, userId: String, session: Session) {
        logger.info { "User($userId) to Game($rematchGameId) | Start" }

        val game = activeGames[rematchGameId] ?: throw GameNotFound(rematchGameId, "Tried to add player a rematch but the game is not found in activeGames!")
        when {
            !game.player1.connectedToRematch -> {
                game.player1.connectedToRematch = true
                game.player1.userId = userId
                game.player1.session = session
                game.player1.session.asyncRemote.sendObject(Init(gameId = rematchGameId))
            }

            !game.player2.connectedToRematch -> {
                game.player2.connectedToRematch = true
                game.player2.userId = userId
                game.player2.session = session
                game.player1.session.asyncRemote.sendObject(Init(type = GamePhase.START, color = game.player1.color.lowercase(), gameId = rematchGameId))
                game.player2.session.asyncRemote.sendObject(Init(type = GamePhase.START, color = game.player2.color.lowercase(), gameId = rematchGameId))
            }

            else -> {
                throw InvalidGameState("State problem, both players have opened sessions, the game should have started!").also {
                    logger.error { it.message }
                }
            }
        }
    }

    override fun connectToCustomGame(customGameId: String, isCreator: String?, userId: String, session: Session) {
        logger.info { "User($userId) connects to game: $customGameId | Start" }

        when (val game = activeGames[customGameId]) {
            null -> {
                isCreator ?: throw BadCustomGameRequest("Game $customGameId doesn't exist | Exit").also {
                    logger.warn { it.message }
                }

                activeGames[customGameId] = Game(
                    Player(userId = userId, color = Color.W).apply { this.session = session },
                    Player(color = Color.B)
                )
                session.asyncRemote.sendObject(Init(gameId = customGameId))
            }

            else -> {
                game.player2.userId = userId
                game.player2.session = session
                game.player1.session.asyncRemote.sendObject(Init(type = GamePhase.START, color = game.player1.color.lowercase(), gameId = customGameId))
                game.player2.session.asyncRemote.sendObject(Init(type = GamePhase.START, color = game.player2.color.lowercase(), gameId = customGameId))
            }
        }
    }

    override fun closeGame(endMessage: End) {
        val gameId = endMessage.gameId
        logger.info { "Closing Connection for Game($gameId) | Start"  }

        broadcast(endMessage)
        // could do a security check if the session matches a session in the active game that needs to be closed
        val removedGame = activeGames.remove(gameId)
            ?: return

        waitingPlayers.remove(removedGame.player1)
        waitingPlayers.remove(removedGame.player2)

        if (removedGame.player1.session.isOpen) {
            removedGame.player1.session.close()
        }
        if (removedGame.player2.session.isOpen) {
            removedGame.player2.session.close()
        }
    }

    override fun getGame(gameId: String): Game = activeGames[gameId] ?: throw GameNotFound(gameId, "Cannot find active Game($gameId)")

    /** Puts a new Game object in the map so that connectToRematchGame() is able to put users in the Game.
     *  Also switches the colors of players */
    override fun offerRematch(gameId: String, userId: String) {
        logger.info { "User($userId) offering rematch | Start" }

        val currentGame = activeGames[gameId]
            ?: throw GameNotFound(gameId, "User offers a rematch but there is no game in activeGames!").also {
                logger.error { it.message }
            }
        when {
            currentGame.player1.userId == userId -> currentGame.player1.offeredRematch = true
            currentGame.player2.userId == userId -> currentGame.player2.offeredRematch = true
            else -> throw InvalidGameState("User($userId), tried offering a rematch in Game($gameId) but he is not in this game! State error!").also {
                logger.error { it.message }
            }
        }

        if (currentGame.player1.offeredRematch && currentGame.player2.offeredRematch) {
            val rematchGameId = UUID.randomUUID().toString()
            activeGames[rematchGameId] = Game(
                player1 = Player(color = currentGame.player2.color),
                player2 = Player(color = currentGame.player1.color),
            )
            currentGame.player1.session.asyncRemote.sendObject(End(gameId = gameId, rematchSuccess = true, rematchGameId = rematchGameId))
            currentGame.player2.session.asyncRemote.sendObject(End(gameId = gameId, rematchSuccess = true, rematchGameId = rematchGameId))

            logger.info { "Rematch accepted, starting a new Game($rematchGameId) | Exit" }
        }
    }

    override fun changePlayerName(userId: String, changeNameMessage: ChangeName) {
        logger.debug { "User($userId) Change name: $changeNameMessage | Start" }

        val game = activeGames[changeNameMessage.gameId]
            ?: throw GameNotFound(changeNameMessage.gameId, "Cannot find active Game(${changeNameMessage.gameId})")

        when {
            game.player1.userId == userId -> {
                game.player1.username = changeNameMessage.name
                game.player2.session.asyncRemote.sendObject(changeNameMessage)
            }

            game.player2.userId == userId -> {
                game.player2.username = changeNameMessage.name
                game.player1.session.asyncRemote.sendObject(changeNameMessage)
            }
        }
    }

    override fun endGame(message: End, userId: String) {
        val game = activeGames[message.gameId]
            ?: throw GameNotFound(message.gameId, "Cannot find active Game(${message.gameId})")

        game.state.gameOver = message.gameOver
    }

    override fun broadcast(message: Message) {
        when (message) {
            is Move -> broadcastMove(message)
            is End -> broadcastEnd(message)
            else -> {}
        }
    }

    fun broadcastMove(moveResult: Move) {
        logger.debug { "Sending $moveResult" }

        activeGames[moveResult.gameId]?.let {
            it.player1.session.asyncRemote.sendObject(moveResult)
            it.player2.session.asyncRemote.sendObject(moveResult)
        }
    }

    fun broadcastEnd(endMessage: End) {
        logger.info { "Sending $endMessage" }

        activeGames[endMessage.gameId]?.let { game ->
            game.player1.session.asyncRemote.sendObject(endMessage.apply { this.gameOver = game.state.gameOver })
            // When game is in init phase and a player is waiting session is not initialised and if the waiting player leaves,
            // the room can't close properly because session access throws
            if (game.player2.userId != null) {
                game.player2.session.asyncRemote.sendObject(endMessage.apply { this.gameOver = game.state.gameOver })
            }
        }
    }

    override fun getWaitingPlayers() = LinkedList(waitingPlayers.map { it.copy() })
    override fun getActiveGames() = HashMap(activeGames)
    override fun closeGameUponClientSessionEnd(userId: String) {
        waitingPlayers.removeIf { it.userId == userId }
        val gameId = activeGames.toList().firstOrNull {
            it.second.player1.userId == userId ||
            it.second.player2.userId == userId
        }?.first

        if (gameId != null) {
            activeGames.remove(gameId)
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
    var userId: String? = null,
    var username: String? = null,
    var color: Color,
    var offeredRematch: Boolean = false,
    var connectedToRematch: Boolean = false
) {
    lateinit var session: Session
}

