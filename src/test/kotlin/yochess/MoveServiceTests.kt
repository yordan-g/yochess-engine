package yochess

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import yochess.dtos.End
import yochess.dtos.Move
import yochess.services.*
import yochess.services.GameState.Companion.EMPTY_MOVE_REQUEST
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

@QuarkusTest
class MoveServiceTests {
    private val logger = KotlinLogging.logger {}

    @Inject
    lateinit var moveService: MoveService

    /**
     *  Parse thousands of real life games ending in checkmate from files.
     *  Run every one through the MoveService.
     *  Assert if yochess game is in end state matching a real game.
     *  */
    @Test
    fun `GIVEN many real games ending in checkmate THEN yochess end state matches a real game`() {
        val realGames = readFromFile("checkmate-15000.txt")

        realGames.forEachIndexed { gIn, realGame ->
            logger.info("### Start Testing Game N: ${gIn + 1}")
            val yochessGameState = GameState()
            var endResult: End? = null

            realGame.moves.forEachIndexed { mIn, move ->
                val from = move.slice(0..1).toXY()
                val to = move.slice(2..3).toXY()
                logger.debug { "## MOVE: $move -- $from to $to. From P: ${yochessGameState.board[from].signature()} | index: $mIn" }

                val (moveResult, end) = moveService.processMove(yochessGameState, from, to, getMoveRequest(move))
                moveResult.valid shouldBe true
                endResult = end
            }

            val yochessEndPieces = yochessGameState.wPieces.size + yochessGameState.bPieces.size
            withClue(
                "Failed to validate Game N: ${gIn + 1}.\n" +
                    " State - Yochess: '$yochessEndPieces', Real: ${realGame.endPieces.length}\n" +
                    "'${realGame}'"
            ) {
                endResult.shouldNotBeNull()
                endResult?.gameOver?.result shouldBe "Checkmate!"
                yochessEndPieces shouldBe realGame.endPieces.length
                yochessGameState.turn.name shouldBe realGame.winner
            }
        }
    }

    @Test
    fun `GIVEN many real games ending in stalemate THEN yochess end state matches a real game`() {
        val realGames = readFromFile("stalemate.txt")

        realGames.forEachIndexed { gIn, realGame ->
            logger.info("### Start Testing Game N: ${gIn + 1}")
            val yochessGameState = GameState()
            var endResult: End? = null

            realGame.moves.forEachIndexed { mIn, move ->
                val from = move.slice(0..1).toXY()
                val to = move.slice(2..3).toXY()
                logger.debug { "## MOVE: $move -- $from to $to. From P: ${yochessGameState.board[from].signature()} | index: $mIn" }

                val (moveResult, end) = moveService.processMove(yochessGameState, from, to, getMoveRequest(move))
                moveResult.valid shouldBe true
                endResult = end
            }

            val yochessEndPieces = yochessGameState.wPieces.size + yochessGameState.bPieces.size
            withClue(
                "Failed to validate Game N: ${gIn + 1}.\n" +
                    " State - Yochess: '$yochessEndPieces', Real: ${realGame.endPieces.length}\n" +
                    "'${realGame}'"
            ) {
                endResult.shouldNotBeNull()
                endResult?.gameOver?.result shouldBe "Stalemate!"
                yochessEndPieces shouldBe realGame.endPieces.length
            }
        }
    }

    /**
     * input format, UCI notation - "... h6g5 h8h4 { "8/5p2/3p2p1/3Pp1k1/3nP1PQ/5PK1/6N1/3q4 b - - 3 51" } 1-0"
     * **/
    private fun readFromFile(name: String): List<RealGame> {
        val url = this::class.java.classLoader.getResource(name) ?: throw FileNotFoundException("test-file1.txt not found")
        val path = Paths.get(url.toURI())

        return Files.newBufferedReader(path).use { reader ->
            reader.useLines { lines ->
                lines.map { game ->
                    with(game.trim()) {
                        RealGame(
                            moves = dropLastWhile { it != '{' }.dropLast(2).split(" ").toList(),
                            winner = takeLast(3).takeLast(1).let {
                                when (it.toInt()) {
                                    1 -> "B"
                                    0 -> "W"
                                    else -> "D"
                                }
                            },
                            endPieces = takeLastWhile { it != '{' }.trim().takeWhile { it != ' ' }.filter { it.isLetter() }
                        )
                    }
                }.toList()
            }
        }
    }

    private fun getMoveRequest(move: String): Move {
        return if (move.length == 5) {
            EMPTY_MOVE_REQUEST.copy(promotion = "x${move.last()}")
        } else {
            EMPTY_MOVE_REQUEST
        }
    }
}

data class RealGame(
    val moves: List<String>,
    val winner: String,
    val endPieces: String
)

operator fun Array<Array<Piece>>.get(p: XY): Piece = this[p.y][p.x]

operator fun Array<Array<Piece>>.set(p: XY, piece: Piece) {
    this[p.y][p.x] = piece
}