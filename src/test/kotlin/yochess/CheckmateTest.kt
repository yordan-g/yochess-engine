package yochess

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import yochess.dtos.Move
import yochess.services.*
import yochess.services.GameState.Companion.EMPTY_MOVE_REQUEST
import java.nio.file.Files
import java.nio.file.Paths

@QuarkusTest
class CheckmateTest {

    @Inject
    lateinit var moveService: MoveService

    @Test
    fun checkMateTest() {
        val games = readFromFile()

        games.forEachIndexed { index, (moves, resultStr) ->
            println("### Start Game: ${index + 1}")

            val gameStateTest = GameState()
            var moveRes: Move? = null
            moves.forEachIndexed { index1, move ->
                val mr = if (move.length == 5) {
                    EMPTY_MOVE_REQUEST.copy(promotion = "x${move.last()}")
                } else {
                    EMPTY_MOVE_REQUEST
                }

                val from = move.slice(0..1).toXY()
                val to = move.slice(2..3).toXY()

                println("## MOVE: $move -- $from to $to. From P: ${gameStateTest.board[from].signature()} | index: $index1")
                moveRes = moveService.processMove(gameStateTest, from, to, mr)
            }

            assertEquals("Checkmate", moveRes?.end, "Optional assertion message")
            println("### Valid Game: ${index + 1}")
        }
    }

    private fun readFromFile(): List<Pair<List<String>, String>> {
        val url = this::class.java.classLoader.getResource("test-file1.txt")
        val path = Paths.get(url.toURI())
        val lines = Files.newBufferedReader(path).use { reader ->
            reader.readLines()
        }

        return lines.map { game ->
            with(game.trim()) {
                val result = game.takeLast(3)
                dropLast(4).split(" ").toList() to result
            }
        }
    }

}

operator fun Array<Array<Piece>>.get(p: XY): Piece = this[p.y][p.x]

operator fun Array<Array<Piece>>.set(p: XY, piece: Piece) {
    this[p.y][p.x] = piece
}