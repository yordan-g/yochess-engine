package yochess

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.jboss.logging.Logger
import org.junit.jupiter.api.Test
import yochess.dtos.Move
import yochess.services.*
import yochess.services.GameState.Companion.EMPTY_MOVE_REQUEST
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

@QuarkusTest
class CheckmateTest {
    private val logger: Logger = Logger.getLogger(this::class.java)

    @Inject
    lateinit var moveService: MoveService

    /**
     *  Parse thousands of real life games ending in checkmate from files.
     *  Run every one through the MoveService.
     *  Assert if yochess game is in end state matching a real game.
     *  */
    @Test
    fun `GIVEN many real games ending in checkmate THEN yochess end state matches a real game`() {
        val realGames = readFromFile()

        realGames.forEachIndexed { gIn, realGame ->
            logger.info("### Start Testing Game N: ${gIn + 1}")
            val yochessGameState = GameState()
            var moveResult: Move? = null

            realGame.moves.forEachIndexed { mIn, move ->
                val from = move.slice(0..1).toXY()
                val to = move.slice(2..3).toXY()
                logger.debug("## MOVE: $move -- $from to $to. From P: ${yochessGameState.board[from].signature()} | index: $mIn")

                moveResult = moveService.processMove(yochessGameState, from, to, getMoveRequest(move))
                moveResult!!.valid shouldBe true
            }

            val yochessEndPieces = yochessGameState.wPieces.size + yochessGameState.bPieces.size
            withClue(
                "Failed to validate Game N: ${gIn + 1}.\n" +
                    " State - Yochess: '$yochessEndPieces', Real: ${realGame.endPieces.length}\n" +
                    "'${realGame}'"
            ) {
                moveResult?.end shouldBe "Checkmate"
                yochessEndPieces shouldBe realGame.endPieces.length
                yochessGameState.turn shouldBe realGame.winner
            }
        }
    }

    /**
     * input format, UCI notation - "... h6g5 h8h4 { "8/5p2/3p2p1/3Pp1k1/3nP1PQ/5PK1/6N1/3q4 b - - 3 51" } 1-0"
     * **/
    private fun readFromFile(): List<RealGame> {
        val url = this::class.java.classLoader.getResource("checkmate-15000.txt") ?: throw FileNotFoundException("test-file1.txt not found")
        val path = Paths.get(url.toURI())

        return Files.newBufferedReader(path).use { reader ->
            reader.useLines { lines ->
                lines.map { game ->
                    with(game.trim()) {
                        RealGame(
                            moves = dropLastWhile { it != '{' }.dropLast(2).split(" ").toList(),
                            winner = takeLast(3).take(1).let { if (it.toInt() == 1) Color.W else Color.B },
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
    val winner: Color,
    val endPieces: String
)

operator fun Array<Array<Piece>>.get(p: XY): Piece = this[p.y][p.x]

operator fun Array<Array<Piece>>.set(p: XY, piece: Piece) {
    this[p.y][p.x] = piece
}