package yochess

import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import yochess.dtos.Move
import yochess.services.*
import yochess.services.GameState.Companion.EMPTY_MOVE_REQUEST
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

@QuarkusTest
class CheckmateTest {
    // todo: Idea for bug checking. Parse lichess DB with only the url tag so that if a game fails in the test.
    //  I print the url and inspect the game state vs my game state
    // todo: Idea 2: alongside UCI notation, parse 2nd file with FEN notation of the endgame. Can compare the pieces left in the FEN and my game state.

    @Inject
    lateinit var moveService: MoveService

    @Test
    fun checkMateTest() {
        val games = readFromFile()

        games.forEachIndexed { gIn, (moves, resultStr) ->
            println("### Start Game: ${gIn + 1}")
            val gameStateTest = GameState()
            var moveRes: Move? = null

            moves.forEachIndexed { mIn, move ->
                val from = move.slice(0..1).toXY()
                val to = move.slice(2..3).toXY()

                println("## MOVE: $move -- $from to $to. From P: ${gameStateTest.board[from].signature()} | index: $mIn")
                moveRes = moveService.processMove(gameStateTest, from, to, getMoveRequest(move))
            }

            moveRes?.end shouldBe "Checkmate"
//            (gameStateTest.wPieces.size + gameStateTest.bPieces.size) shouldBe 5
//            gameStateTest.turn shouldBe Color.W

            println("### Valid Game: ${gIn + 1}")
        }
    }

    private fun readFromFile(): List<Pair<List<String>, String>> {
        val url = this::class.java.classLoader.getResource("test-file1.txt") ?: throw FileNotFoundException("test-file1.txt not found")
        val path = Paths.get(url.toURI())

        return Files.newBufferedReader(path).use { reader ->
            reader.useLines { lines ->
                lines.map { game ->
                    with(game.trim()) {
                        val result = takeLast(3)
                        dropLast(4).split(" ").toList() to result
                    }
                }.toList() // Convert the sequence to a list
            }
        }
    }


//    private fun readFromFile(): List<Pair<List<String>, String>> {
//        val url = this::class.java.classLoader.getResource("test-file1.txt")
//        val path = Paths.get(url.toURI())
//        val lines = Files.newBufferedReader(path).use { reader ->
//            reader.readLines()
//        }
//
//        return lines.map { game ->
//            with(game.trim()) {
//                val result = game.takeLast(3)
//                dropLast(4).split(" ").toList() to result
//            }
//        }
//    }

    private fun getMoveRequest(move: String): Move {
        return if (move.length == 5) {
            EMPTY_MOVE_REQUEST.copy(promotion = "x${move.last()}")
        } else {
            EMPTY_MOVE_REQUEST
        }
    }
}


//withClue("Expected value was $expectedValue, but actual value was $actualValue") {
//    actualValue shouldBe expectedValue // This assertion will include the clue if it fails
//}
operator fun Array<Array<Piece>>.get(p: XY): Piece = this[p.y][p.x]

operator fun Array<Array<Piece>>.set(p: XY, piece: Piece) {
    this[p.y][p.x] = piece
}