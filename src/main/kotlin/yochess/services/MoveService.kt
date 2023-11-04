package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import yochess.dtos.Move
import yochess.services.XY.Companion.idxToFile
import yochess.services.XY.Companion.idxToRank
import kotlin.IllegalArgumentException
import kotlin.math.abs

interface MoveService {
    fun makeMove(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move
}

@ApplicationScoped
class DefaultMoveService : MoveService {
    private val logger: Logger = Logger.getLogger(this::class.java)

    override fun makeMove(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        return gameState
            .board[from]
            .move(gameState, from, to, moveRequest)
            .also {
                gameState.board.print()
                println("Valid: ${it.valid}")
            }
    }
}

sealed interface Piece {
    val color: Color
    fun clone(): Piece
    fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move
    fun signature(): String
    fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean
}

class Pawn(override val color: Color) : Piece {
    private val direction: Int = if (color == Color.W) 1 else -1
    private val startingRank: Int = if (color == Color.W) 1 else 6
    private val promotionRank = if (color == Color.W) 7 else 0

    override fun clone() = Pawn(color)

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        var moveResult = moveRequest.copy(valid = false)
        var capturedPiece: Piece = EM
        var enPassantCapturePosition: XY? = null

        when {
            // 1. One-step forward
            (to.y == from.y + direction && to.x == from.x && gameState.board[to] is EM)
            -> {
                gameState.board[from] = EM
                if (to.y == promotionRank) {
                    gameState.board[to] = promotePawn(moveRequest.promotion)
                } else {
                    gameState.board[to] = this.clone()
                }
                moveResult = moveRequest.copy(valid = true)
            }
            // 2. Initial Two-step forward
            (from.y == startingRank && to.y == from.y + (2 * direction) && to.x == from.x && gameState.board[to] is EM)
            -> {
                gameState.board[to] = this.clone()
                gameState.board[from] = EM
                gameState.enPassantTarget = XY(from.x, from.y + direction)
                moveResult = moveRequest.copy(valid = true)
            }
            // 3. Capture Diagonally
            (to.y == from.y + direction && (to.x == from.x + 1 || to.x == from.x - 1) &&
                gameState.board[to] !is EM && gameState.board[to].color != color)
            -> {
                gameState.board[from] = EM
                if (to.y == promotionRank) {
                    capturedPiece = gameState.board[to].clone()
                    gameState.board[to] = promotePawn(moveRequest.promotion)
                } else {
                    capturedPiece = gameState.board[to].clone()
                    gameState.board[to] = this.clone()
                }
                moveResult = moveRequest.copy(valid = true)
            }
            // 4. En passant
            (to.y == from.y + direction && (to.x == from.x + 1 || to.x == from.x - 1) && gameState.board[to] is EM &&
                gameState.board[XY(to.x, from.y)] is Pawn && gameState.enPassantTarget == to)
            -> {
                // todo this pos is different than other captures of a pawn, if need to be reverted won't work
                if (gameState.board[XY(to.x, from.y)] is Pawn && gameState.enPassantTarget == to) {
                    enPassantCapturePosition = XY(to.x, from.y)
                    gameState.board[to] = this.clone()
                    gameState.board[from] = EM
                    capturedPiece = gameState.board[enPassantCapturePosition]
                    gameState.board[enPassantCapturePosition] = EM
                }
            }
            // 5. Invalid move
            else -> return moveResult
        }

        // 6. Is the king under a check after move
        gameState.getTheKing(color)
            .isTheKingSafeAfterPieceMoved(gameState)
            .also {
                if (!it) gameState.revertMove(capturedPiece, from, to, enPassantCapturePosition)
                moveResult = moveRequest.copy(valid = it, enPassantCapture = enPassantCapturePosition?.toFileRank() ?: null)
            }

        return moveResult
    }

    private fun promotePawn(promotion: String?): Piece {
        return when (val piece = promotion?.lastOrNull()) {
            'q' -> Queen(color)
            'r' -> Rook(color)
            'n' -> Knight(color)
            'b' -> Bishop(color)
            null -> throw IllegalArgumentException("Error: Promotion piece not provided!")
            else -> throw IllegalArgumentException("Error: Piece: $piece is not a valid chess notation!")
        }
    }

    override fun signature(): String = if (color == Color.W) "WP" else "BP"

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        return false
    }
}

class Queen(override val color: Color) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        gameState.enPassantTarget = null
        return gameState.tryMakeMove(this, from, to, moveRequest)
    }

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y

        // Check if path is diagonal, vertical, or horizontal
        if (abs(dx) != abs(dy) && (dx != 0 && dy != 0)) {
            return false
        }

        val stepX = if (dx != 0) dx / abs(dx) else 0
        val stepY = if (dy != 0) dy / abs(dy) else 0

        var currentX = from.x + stepX
        var currentY = from.y + stepY

        // Check the path for other pieces until the destination
        while (currentX != to.x || currentY != to.y) {
            if (board[currentY][currentX] != EM) {
                return false // Path is blocked
            }

            currentX += stepX
            currentY += stepY
        }

        val destinationPiece = board[to]
        // Can't capture own pieces
        if (destinationPiece != EM && destinationPiece.color == this.color) return false

        return true
    }

    override fun clone() = Queen(color)
    override fun signature(): String = if (color == Color.W) "WQ" else "BQ"
}

class King(override val color: Color) : Piece {
    override fun clone() = King(color)

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        gameState.enPassantTarget = null

        gameState.board[to] = this.clone()
        gameState.board[from] = EM
        when (color) {
            Color.W -> gameState.positionWK = to
            Color.B -> gameState.positionBK = to
        }

        return moveRequest.copy(valid = true)
    }

    fun isTheKingSafeAfterPieceMoved(gameState: GameState): Boolean {
        val kingPosition = gameState.getKingPosition(color)
        val directions = listOf(
            XY(0, -1),   // North
            XY(1, -1),   // North-East
            XY(1, 0),    // East
            XY(1, 1),    // South-East
            XY(0, 1),    // South
            XY(-1, 1),   // South-West
            XY(-1, 0),   // West
            XY(-1, -1)   // North-West
        )

        for (direction in directions) {
            var distance = 1
            while (true) {
                val moveTo = XY(kingPosition.x + direction.x * distance, kingPosition.y + direction.y * distance)

                if (!isValidSquare(moveTo)) break
                val piece = gameState.board[moveTo]

                if (isPinningPiece(piece, kingPosition, moveTo)) return false
                if (isAlliedPiece(piece) || piece !is EM) break

                distance++
            }
        }

        return true
    }

    private fun isValidSquare(square: XY): Boolean = square.x in 0..7 && square.y in 0..7

    private fun isPinningPiece(piece: Piece, kingFrom: XY, kingTo: XY): Boolean {
        return when {
            isVerticalOrHorizontalMove(kingFrom, kingTo) ->
                (piece is Queen || piece is Rook) && piece.color != this.color

            isDiagonalMove(kingFrom, kingTo) ->
                (piece is Queen || piece is Bishop) && piece.color != this.color

            else -> false
        }
    }

    private fun isVerticalOrHorizontalMove(from: XY, to: XY): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y

        // The move is either vertical or horizontal
        return ((dx == 0 && dy != 0) || (dy == 0 && dx != 0))
    }

    private fun isDiagonalMove(from: XY, to: XY): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y

        return dx != 0 && dy != 0 && abs(dx) == abs(dy)
    }

    private fun isAlliedPiece(piece: Piece): Boolean = piece !is EM && piece.color == this.color

    override fun signature(): String = if (color == Color.W) "WK" else "BK"

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        return false
    }
}

class Rook(override val color: Color) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        gameState.enPassantTarget = null
        return gameState.tryMakeMove(this, from, to, moveRequest)
    }

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y

        // Rook moves must be either horizontal or vertical.
        if (dx != 0 && dy != 0) {
            return false
        }

        val stepX = if (dx != 0) dx / abs(dx) else 0
        val stepY = if (dy != 0) dy / abs(dy) else 0

        var currentX = from.x + stepX
        var currentY = from.y + stepY

        // Check the path for other pieces
        while (currentX != to.x || currentY != to.y) {
            if (board[XY(currentX, currentY)] != EM) {
                return false // Path is blocked
            }

            currentX += stepX
            currentY += stepY
        }

        // Check if the destination square is occupied by a friendly piece
        val destinationPiece = board[to]
        if (destinationPiece != EM && destinationPiece.color == this.color) return false

        return true
    }

    override fun clone() = Rook(color)
    override fun signature(): String = if (color == Color.W) "WR" else "BR"
}

class Bishop(override val color: Color) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        gameState.enPassantTarget = null
        return gameState.tryMakeMove(this, from, to, moveRequest)
    }

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y

        // Check if move is diagonal by comparing the absolute values of dx and dy
        if (abs(dx) != abs(dy)) {
            return false // Move is not diagonal
        }

        val stepX = if (dx != 0) dx / abs(dx) else 0
        val stepY = if (dy != 0) dy / abs(dy) else 0

        var currentX = from.x + stepX
        var currentY = from.y + stepY

        // Check the path for other pieces until the destination
        while (currentX != to.x || currentY != to.y) {
            if (board[currentY][currentX] != EM) {
                return false // Path is blocked
            }

            currentX += stepX
            currentY += stepY
        }

        val destinationPiece = board[to]
        // Can't capture own pieces
        if (destinationPiece != EM && destinationPiece.color == this.color) return false

        return true
    }

    override fun clone() = Bishop(color)
    override fun signature(): String = if (color == Color.W) "WB" else "BB"
}

class Knight(override val color: Color) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        gameState.enPassantTarget = null
        return gameState.tryMakeMove(this, from, to, moveRequest)
    }

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        val dx = abs(to.x - from.x)
        val dy = abs(to.y - from.y)

        // Check for L-shaped move (2 squares in one direction and 1 square in perpendicular direction)
        if (!((dx == 2 && dy == 1) || (dx == 1 && dy == 2))) {
            return false
        }

        val destinationPiece = board[to.y][to.x]
        // Can't move to a square with own piece
        if (destinationPiece != EM && destinationPiece.color == this.color) return false

        return true
    }

    override fun clone() = Knight(color)
    override fun signature(): String = if (color == Color.W) "WN" else "BN"
}

object EM : Piece {
    override val color: Color get() = throw UnsupportedOperationException("EM does not have a color")
    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move = moveRequest.copy(valid = false)
    override fun clone() = EM
    override fun signature(): String = "EM"
    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean = false
}

class GameState {
    val board: Array<Array<Piece>> = initBoard()
    var positionWK: XY = XY(3, 0)
    var positionBK: XY = XY(3, 7)
    var enPassantTarget: XY? = null

    fun getTheKing(color: Color): King =
        when (val piece = board[getKingPosition(color)]) {
            is King -> piece
            else -> throw IllegalArgumentException("Board state error, there is no King at position ${getKingPosition(color)}")
        }

    fun getKingPosition(color: Color): XY = when (color) {
        Color.W -> positionWK
        Color.B -> positionBK
    }

    /** Returns the captured piece or EM */
    private fun makeMove(from: XY, to: XY): Piece {
        return this.board[to].clone().also {
            this.board[to] = this.board[from].clone()
            this.board[from] = EM
        }
    }

    fun tryMakeMove(piece: Piece, from: XY, to: XY, moveRequest: Move): Move {
        return when (piece.isValidMove(board, from, to)) {
            true -> {
                makeMove(from, to).let { capturedPiece ->
                    getTheKing(piece.color)
                        .isTheKingSafeAfterPieceMoved(this)
                        .let { isKingSafe ->
                            if (!isKingSafe) revertMove(capturedPiece, from, to, null)
                            moveRequest.copy(valid = isKingSafe)
                        }
                }
            }
            false -> moveRequest.copy(valid = false)
        }
    }

    fun revertMove(capturedPiece: Piece, from: XY, to: XY, enPassantCapturePosition: XY?) {
        if (enPassantCapturePosition != null) {
            board[from] = board[to].clone()
            board[to] = EM
            board[enPassantCapturePosition] = capturedPiece.clone()
        } else {
            board[from] = board[to].clone()
            board[to] = capturedPiece.clone()
        }
    }

    companion object {
        val WP = Pawn(Color.W)
        val WR = Rook(Color.W)
        val WN = Knight(Color.W)
        val WB = Bishop(Color.W)
        val WQ = Queen(Color.W)
        val WK = King(Color.W)
        val BP = Pawn(Color.B)
        val BR = Rook(Color.B)
        val BN = Knight(Color.B)
        val BB = Bishop(Color.B)
        val BQ = Queen(Color.B)
        val BK = King(Color.B)

        val STARTING_BOARD: Array<Array<Piece>> =
            arrayOf(
                //     (a , b , c , d , e , f , g , h ),
                //     (0 , 1 , 2 , 3 , 4 , 5 , 6 , 7 ),
                arrayOf(WR, WN, WB, WK, WQ, WB, WN, WR), // 1 | 0
                arrayOf(WP, WP, WP, WP, WP, WP, WP, WP), // 2 | 1
                arrayOf(EM, EM, EM, EM, EM, EM, EM, EM), // 3 | 2
                arrayOf(EM, EM, EM, EM, EM, EM, EM, EM), // 4 | 3
                arrayOf(EM, EM, EM, EM, EM, EM, EM, EM), // 5 | 4
                arrayOf(EM, EM, EM, EM, EM, EM, EM, EM), // 6 | 5
                arrayOf(BP, BP, BP, BP, BP, BP, BP, BP), // 7 | 6
                arrayOf(BR, BN, BB, BK, BQ, BB, BN, BR), // 8 | 7
                //     (h , g , f , e , d , c , b , a ),
                //     (0 , 1 , 2 , 3 , 4 , 5 , 6 , 7 ),
            )

        private fun initBoard() = STARTING_BOARD.map { row ->
            row.map { it.clone() }.toTypedArray()
        }.toTypedArray()
    }
}

operator fun Array<Array<Piece>>.get(p: XY): Piece = this[p.y][p.x]

operator fun Array<Array<Piece>>.set(p: XY, piece: Piece) {
    this[p.y][p.x] = piece
}

fun Array<Array<Piece>>.print() {
    this.forEach { row ->
        var str = ""
        row.forEach { p ->
            str += "${p.signature()}, "
        }
        println(str)
    }
}

enum class Color { W, B }

data class XY(val x: Int, val y: Int) {
    companion object {
        val idxToRank = mapOf(
            0 to "1",
            1 to "2",
            2 to "3",
            3 to "4",
            4 to "5",
            5 to "6",
            6 to "7",
            7 to "8",
        )

        val idxToFile = mapOf(
            0 to "h",
            1 to "g",
            2 to "f",
            3 to "e",
            4 to "d",
            5 to "c",
            6 to "b",
            7 to "a",
        )
    }
}

fun XY.toFileRank(): String = (idxToFile[x] + idxToRank[y]).also {
    if (it.length > 2) throw IllegalArgumentException("Can't convert to File:Rank from the given indexes: $x:$y")
}


fun String.toXY(): XY {
    // todo Check if the destination square is within the board's boundaries
    //    if (to.x !in 0..7 || to.y !in 0..7) return false
    return this.map {
        when (it) {
            'h' -> 0
            'g' -> 1
            'f' -> 2
            'e' -> 3
            'd' -> 4
            'c' -> 5
            'b' -> 6
            'a' -> 7

            '1' -> 0
            '2' -> 1
            '3' -> 2
            '4' -> 3
            '5' -> 4
            '6' -> 5
            '7' -> 6
            '8' -> 7
            else -> throw IllegalArgumentException("Char $it isn't a valid chess square notation!")
        }
    }.let {
        XY(x = it.first(), y = it.last())
    }
}