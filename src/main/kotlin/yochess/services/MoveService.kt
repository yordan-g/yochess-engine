package yochess.services

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import yochess.dtos.Castle
import yochess.dtos.Move
import yochess.services.GameState.Companion.DIRECTIONS
import yochess.services.XY.Companion.idxToFile
import yochess.services.XY.Companion.idxToRank
import java.util.*
import kotlin.IllegalArgumentException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface MoveService {
    fun makeMove(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move
}

@ApplicationScoped
class DefaultMoveService : MoveService {
    private val logger: Logger = Logger.getLogger(this::class.java)

    override fun makeMove(gameState: GameState, from: XY, to: XY, moveRequest: Move): Move {
        if (gameState.turn.toString() != moveRequest.piece.first().uppercase()) {
            return moveRequest.copy(valid = false)
        }

        return gameState
            .board[from]
            .move(gameState, from, to, moveRequest)
            .let { (move, capturedPiece) ->
                if (move.valid == true) {
                    gameState.logMove(moveRequest, capturedPiece.id)
                    gameState.track(capturedPiece, gameState.board[to] to to)


                    val opponentColor = gameState.getOpponentColor()
                    val opponentKing = gameState.getTheKing(opponentColor)

//                    opponentKing.isInCheckCheckmateStalemate(gameState, gameState.getKingPosition(opponentColor))

                    gameState.changeTurn()
                }

                gameState.board.print()
                println("Valid: ${move.valid}")
                move
            }
    }
}

sealed interface Piece {
    val color: Color
    val id: String
    fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece>
    fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean
    fun clone(): Piece
    fun signature(): String
}

class Pawn(override val color: Color, override val id: String) : Piece {
    private val direction: Int = if (color == Color.W) 1 else -1
    private val startingRank: Int = if (color == Color.W) 1 else 6
    private val promotionRank = if (color == Color.W) 7 else 0

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
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
                    gameState.board[to] = this
                }
                moveResult = moveRequest.copy(valid = true)
            }
            // 2. Initial Two-step forward
            (from.y == startingRank && to.y == from.y + (2 * direction) && to.x == from.x && gameState.board[to] is EM)
            -> {
                gameState.board[to] = this
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
                    capturedPiece = gameState.board[to]
                    gameState.board[to] = promotePawn(moveRequest.promotion)
                } else {
                    capturedPiece = gameState.board[to]
                    gameState.board[to] = this
                }
                moveResult = moveRequest.copy(valid = true)
            }
            // 4. En passant
            (to.y == from.y + direction && (to.x == from.x + 1 || to.x == from.x - 1) && gameState.board[to] is EM &&
                gameState.board[XY(to.x, from.y)] is Pawn && gameState.enPassantTarget == to)
            -> {
                if (gameState.board[XY(to.x, from.y)] is Pawn && gameState.enPassantTarget == to) {
                    enPassantCapturePosition = XY(to.x, from.y)
                    gameState.board[to] = this
                    gameState.board[from] = EM
                    capturedPiece = gameState.board[enPassantCapturePosition]
                    gameState.board[enPassantCapturePosition] = EM
                }
            }
            // 5. Invalid move
            else -> return moveResult to EM
        }

        // 6. Is the king under a check after move
        gameState.getTheKing(color)
            .isTheKingSafeAfterPieceMoved(gameState)
            .also {
                if (!it) gameState.revertMove(capturedPiece, from, to, enPassantCapturePosition)
                moveResult = moveRequest.copy(valid = it, enPassantCapture = enPassantCapturePosition?.toFileRank() ?: null)
            }

        return moveResult to capturedPiece
    }

    private fun promotePawn(promotion: String?): Piece {
        return when (val piece = promotion?.lastOrNull()) {
            // todo - increment unique ids here because it will break tracking of active pieces
            'q' -> Queen(color, id)
            'r' -> Rook(color, id).apply { hasMoved = true }
            'n' -> Knight(color, id)
            'b' -> Bishop(color, id)
            null -> throw IllegalArgumentException("Error: Promotion piece not provided!")
            else -> throw IllegalArgumentException("Error: Piece: $piece is not a valid chess notation!")
        }
    }

    override fun clone() = Pawn(color, id)
    override fun signature(): String = if (color == Color.W) "WP" else "BP"
    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        return false
    }
}

class Queen(override val color: Color, override val id: String) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
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

    override fun clone() = Queen(color, id)
    override fun signature(): String = if (color == Color.W) "WQ" else "BQ"
}

class King(override val color: Color, override val id: String) : Piece {
    private val notCastleAttempt = Triple(false, XY(-1, -1), XY(-1, -1))
    private var hasMoved = false

    fun isInCheckCheckmateStalemate(gameState: GameState, kingPosition: XY): Triple<Boolean, Boolean, Boolean> {
        val inCheck = isInCheck(gameState, kingPosition)
        var inCheckmate = false
        var inStalemate = false

        if (inCheck) {
            inCheckmate = !hasLegalMoves(gameState, kingPosition, inCheck)
        } else {
            inStalemate = !hasLegalMoves(gameState, kingPosition, inCheck)
        }

        return Triple(inCheck, inCheckmate, inStalemate)
    }

    private fun hasLegalMoves(gameState: GameState, kingPosition: XY, inCheck: Boolean): Boolean {
        // Check all possible moves for the king and other pieces to escape check or to make a legal move
        // This method needs to consider both the case of check and no check (stalemate scenario)
        // ...

        // If no legal move exists that can get the king out of check or make any legal move, return false
        return false
    }

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
        gameState.enPassantTarget = null

        isCastleAttempt(from, to).let { castleAttempt ->
            if (castleAttempt.first) {
                return gameState
                    .tryKingMove(this, from, to, moveRequest, castleAttempt).also { (move, capturedPiece) ->
                        if (move.valid == true) hasMoved = true
                    }
            }
        }

        return when (isValidMove(gameState.board, from, to)) {
            true ->
                gameState.tryKingMove(this, from, to, moveRequest, notCastleAttempt).also { (move, _) ->
                    if (move.valid == true) hasMoved = true
                }

            false -> moveRequest.copy(valid = false) to EM
        }
    }

    private fun isCastleAttempt(from: XY, to: XY): Triple<Boolean, XY, XY> {
        val dx = abs(to.x - from.x)
        val dy = abs(to.y - from.y)

        // Validate if it's a horizontal move on the first or last rank (row)
        if (dy == 0 && dx == 2 && (from.y == 0 || from.y == 7)) {
            val (rookPosStart, rookPosEnd) = if (to.x > from.x) {
                Pair(XY(7, from.y), XY(x = 4, from.y))
            } else {
                Pair(XY(0, from.y), XY(x = 2, from.y))
            }

            return Triple(true, rookPosStart, rookPosEnd)
        }

        return notCastleAttempt
    }

    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean {
        // King can move only one square in any direction
        val dx = abs(to.x - from.x)
        val dy = abs(to.y - from.y)

        if ((dx > 1) || (dy > 1)) return false

        // Check if the destination square is occupied by an ally
        val destinationPiece = board[to]
        if (destinationPiece != EM && destinationPiece.color == this.color) {
            return false // Can't capture own piece
        }

        return true
    }

    fun canCastle(gameState: GameState, from: XY, to: XY): Boolean {
        if (hasMoved) return false

        val direction = if (to.x > from.x) 1 else -1
        val rookX = if (direction == 1) 7 else 0 // Rook's position for castling

        // Check if the path between the king and rook is clear
        for (x in min(from.x, rookX) + 1 until max(from.x, rookX)) {
            if (gameState.board[XY(x, from.y)] !is EM) return false
        }

        // Check if the king is currently in check, or passes through/ends up in check
        if (isInCheck(gameState, gameState.getKingPosition(this.color))
            || isPassingThroughCheck(gameState, from, direction)
        ) return false

        val rook = gameState.board[XY(rookX, from.y)]
        return rook is Rook && !rook.hasMoved // Check if the rook is present and hasn't moved
    }

    private fun isPassingThroughCheck(gameState: GameState, from: XY, direction: Int): Boolean {
        // Check the two squares the king moves through during castling
        val checkPositions = if (direction == 1) {
            listOf(XY(from.x + 1, from.y), XY(from.x + 2, from.y))
        } else {
            listOf(XY(from.x - 1, from.y), XY(from.x - 2, from.y))
        }

        for (newPos in checkPositions) {
            if (isInCheck(gameState, newPos)) return true
        }

        return false
    }

    fun isTheKingSafeAfterPieceMoved(gameState: GameState): Boolean {
        val kingPosition = gameState.getKingPosition(color)

        for (direction in DIRECTIONS) {
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

    fun isInCheck(gameState: GameState, kingPosition: XY): Boolean {
        for (direction in DIRECTIONS) {
            var distance = 1
            while (true) {
                val moveTo = XY(kingPosition.x + direction.x * distance, kingPosition.y + direction.y * distance)

                if (!isValidSquare(moveTo)) break
                val piece = gameState.board[moveTo]

                if (isPinningPiece(piece, kingPosition, moveTo)) return true
                if (piece !is EM) break // If there is any piece, a friend or foe, stop checking this direction

                distance++
            }
        }

        // Check for knights
        val knightMoves = listOf(
            XY(1, 2), XY(2, 1), XY(-1, 2), XY(-2, 1),
            XY(1, -2), XY(2, -1), XY(-1, -2), XY(-2, -1)
        )
        for (kMove in knightMoves) {
            val knightPos = XY(kingPosition.x + kMove.x, kingPosition.y + kMove.y)
            if (isValidSquare(knightPos)) {
                val piece = gameState.board[knightPos]
                if (piece is Knight && piece.color != this.color) {
                    return true
                }
            }
        }

        // Check for pawns
        val pawnDirections = if (color == Color.W) listOf(XY(-1, 1), XY(1, 1)) else listOf(XY(-1, -1), XY(1, -1))
        for (pDir in pawnDirections) {
            val pawnPos = XY(kingPosition.x + pDir.x, kingPosition.y + pDir.y)
            if (isValidSquare(pawnPos)) {
                val piece = gameState.board[pawnPos]
                if (piece is Pawn && piece.color != this.color) {
                    return true
                }
            }
        }

        // Checking for the enemy king
        for (dir in DIRECTIONS) {
            val checkPos = kingPosition + dir
            if (isValidSquare(checkPos)) {
                val piece = gameState.board[checkPos]
                if ((piece is King) && (piece.color != color)) {
                    return true
                }
            }
        }

        return false
    }

    private operator fun XY.plus(other: XY): XY = XY(x + other.x, y + other.y)

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
    override fun clone() = King(color, id)
    override fun signature(): String = if (color == Color.W) "WK" else "BK"
}

class Rook(override val color: Color, override val id: String) : Piece {
    var hasMoved = false

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
        gameState.enPassantTarget = null
        return gameState.tryMakeMove(this, from, to, moveRequest).also { (move, _) ->
            if (move.valid == true) hasMoved = true
        }
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

    override fun clone() = Rook(color, id)
    override fun signature(): String = if (color == Color.W) "WR" else "BR"
}

class Bishop(override val color: Color, override val id: String) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
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

    override fun clone() = Bishop(color, id)
    override fun signature(): String = if (color == Color.W) "WB" else "BB"
}

class Knight(override val color: Color, override val id: String) : Piece {

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
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

        val destinationPiece = board[to]
        // Can't move to a square with own piece
        if (destinationPiece != EM && destinationPiece.color == this.color) return false

        return true
    }

    override fun clone() = Knight(color, id)
    override fun signature(): String = if (color == Color.W) "WN" else "BN"
}

object EM : Piece {
    override val color: Color get() = throw UnsupportedOperationException("EM does not have a color")
    override val id: String = "em"

    override fun move(gameState: GameState, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> = moveRequest.copy(valid = false) to EM
    override fun clone() = EM
    override fun signature(): String = "EM"
    override fun isValidMove(board: Array<Array<Piece>>, from: XY, to: XY): Boolean = false
}

// piece:from:to:captured
// "wp:e2:e4:bq"
data class MoveLog(
    var w: String,
    var b: String? = null
)

class GameState {
    val board: Array<Array<Piece>> = initBoard()
    var turn: Color = Color.W
    var enPassantTarget: XY? = null

    private var positionWK: XY = XY(3, 0)
    private var positionBK: XY = XY(3, 7)

    private val wPieces: MutableMap<String, XY> = mutableMapOf(
        WR1.id to XY(0, 0), WN1.id to XY(1, 0), WB1.id to XY(2, 0), WK1.id to XY(3, 0),
        WQ1.id to XY(4, 0), WB2.id to XY(5, 0), WN2.id to XY(6, 0), WR2.id to XY(7, 0),
        WP1.id to XY(0, 1), WP2.id to XY(1, 1), WP3.id to XY(2, 1), WP4.id to XY(3, 1),
        WP5.id to XY(4, 1), WP6.id to XY(5, 1), WP7.id to XY(6, 1), WP8.id to XY(7, 1)
    )
    private val bPieces: MutableMap<String, XY> = mutableMapOf(
        BR1.id to XY(0, 7), BN1.id to XY(1, 7), BB1.id to XY(2, 7), BK1.id to XY(3, 7),
        BQ1.id to XY(4, 7), BB2.id to XY(5, 7), BN2.id to XY(6, 7), BR2.id to XY(7, 7),
        BP1.id to XY(0, 6), BP2.id to XY(1, 6), BP3.id to XY(2, 6), BP4.id to XY(3, 6),
        BP5.id to XY(4, 6), BP6.id to XY(5, 6), BP7.id to XY(6, 6), BP8.id to XY(7, 6)
    )
    private val wCaptures = LinkedList<String>()
    private val bCaptures = LinkedList<String>()

    var bInCheck = false
    var wInCheck = false

    private val history = LinkedList<MoveLog>()

    fun track(capturedPiece: Piece, movedPiecePair: Pair<Piece, XY>) {
        movedPiecePair.first.let { movedPiece ->
            when (movedPiece.color) {
                Color.W -> {
                    // todo: could handle the error even thought it's guarded already
                    wPieces.replace(movedPiece.id, movedPiecePair.second)
                    if (capturedPiece != EM) {
                        wCaptures.add(capturedPiece.id)
                        bPieces.remove(capturedPiece.id)
                    }
                }

                Color.B -> {
                    bPieces.replace(movedPiece.id, movedPiecePair.second)
                    if (capturedPiece != EM) {
                        bCaptures.add(capturedPiece.id)
                        wPieces.remove(capturedPiece.id)
                    }
                }
            }
        }
    }

    // "wp:e2:e4:bq"
    fun logMove(moveRequest: Move, capture: String) = when (turn) {
        Color.B -> "${moveRequest.piece}:${moveRequest.squareFrom}:${moveRequest.squareTo}:$capture"
            .also { history.last.b = it }

        Color.W -> "${moveRequest.piece}:${moveRequest.squareFrom}:${moveRequest.squareTo}:$capture"
            .also { history.add(MoveLog(it)) }
    }

    fun getOpponentColor(): Color = if (turn == Color.W) Color.B else Color.W

//    fun setKingInCheck(color: Color) = when (color) {
//        Color.W -> wInCheck = true
//        Color.B -> bInCheck = true
//    }
//
//    fun isKingInCheck(color: Color) = when (color) {
//        Color.W -> wInCheck
//        Color.B -> bInCheck
//    }

    fun changeTurn(): Color = when (turn) {
        Color.W -> {
            turn = Color.B
            turn
        }

        Color.B -> {
            turn = Color.W
            turn
        }
    }

    private fun setKingPosition(color: Color, to: XY) = when (color) {
        Color.W -> positionWK = to
        Color.B -> positionBK = to
    }

    fun getTheKing(color: Color): King = when (val piece = board[getKingPosition(color)]) {
        is King -> piece
        else -> throw IllegalArgumentException("Board state error, there is no King at position ${getKingPosition(color)}")
    }

    fun getKingPosition(color: Color): XY = when (color) {
        Color.W -> positionWK
        Color.B -> positionBK
    }

    /** Returns the captured piece or EM */
    private fun makeMove(from: XY, to: XY): Piece {
        val capturedPiece = this.board[to]
        this.board[to] = this.board[from]
        this.board[from] = EM
        return capturedPiece
    }

    fun tryMakeMove(piece: Piece, from: XY, to: XY, moveRequest: Move): Pair<Move, Piece> {
        return when (piece.isValidMove(board, from, to)) {
            true -> {
                makeMove(from, to).let { capturedPiece ->
                    getTheKing(piece.color)
                        .isTheKingSafeAfterPieceMoved(this)
                        .let { isKingSafe ->
                            if (!isKingSafe) revertMove(capturedPiece, from, to, null)
                            moveRequest.copy(valid = isKingSafe) to capturedPiece
                        }
                }
            }

            false -> moveRequest.copy(valid = false) to EM
        }
    }

    fun tryKingMove(king: King, from: XY, to: XY, moveRequest: Move, castleAttempt: Triple<Boolean, XY, XY>): Pair<Move, Piece> {
        if (castleAttempt.first) {
            return if (king.canCastle(this, from, to)) {
                setKingPosition(king.color, to)
                val capturedPiece = makeMove(from, to)
                makeMove(castleAttempt.second, castleAttempt.third)
                moveRequest.copy(
                    valid = true, castle = Castle(
                        rook = Rook(king.color, id = "${king.color.toString().lowercase()}r").signature().lowercase(),
                        rookPosStart = castleAttempt.second.toFileRank(),
                        rookPosEnd = castleAttempt.third.toFileRank()
                    )
                ) to capturedPiece
            } else {
                moveRequest.copy(valid = false) to EM
            }
        }
        // normal move
        return makeMove(from, to).let { capturedPiece ->
            setKingPosition(king.color, to)
            king.isInCheck(this, getKingPosition(king.color)).let { isInCheck ->
                if (isInCheck) {
                    revertMove(capturedPiece, from, to, null)
                    setKingPosition(king.color, from)
                    moveRequest.copy(valid = false) to EM
                } else {
                    moveRequest.copy(valid = true) to capturedPiece
                }
            }
        }
    }

    fun revertMove(capturedPiece: Piece, from: XY, to: XY, enPassantCapturePosition: XY?) {
        if (enPassantCapturePosition != null) {
            board[from] = board[to]
            board[to] = EM
            board[enPassantCapturePosition] = capturedPiece
        } else {
            board[from] = board[to]
            board[to] = capturedPiece
        }
    }

    companion object {
        val WP1 = Pawn(Color.W, id = "wp1")
        val WP2 = Pawn(Color.W, id = "wp2")
        val WP3 = Pawn(Color.W, id = "wp3")
        val WP4 = Pawn(Color.W, id = "wp4")
        val WP5 = Pawn(Color.W, id = "wp5")
        val WP6 = Pawn(Color.W, id = "wp6")
        val WP7 = Pawn(Color.W, id = "wp7")
        val WP8 = Pawn(Color.W, id = "wp8")
        val WR1 = Rook(Color.W, id = "wr1")
        val WR2 = Rook(Color.W, id = "wr2")
        val WN1 = Knight(Color.W, id = "wn1")
        val WN2 = Knight(Color.W, id = "wn2")
        val WB1 = Bishop(Color.W, id = "wb1")
        val WB2 = Bishop(Color.W, id = "wb2")
        val WQ1 = Queen(Color.W, id = "wq1")
        val WK1 = King(Color.W, id = "wk1")

        val BP1 = Pawn(Color.B, id = "bp1")
        val BP2 = Pawn(Color.B, id = "bp2")
        val BP3 = Pawn(Color.B, id = "bp3")
        val BP4 = Pawn(Color.B, id = "bp4")
        val BP5 = Pawn(Color.B, id = "bp5")
        val BP6 = Pawn(Color.B, id = "bp6")
        val BP7 = Pawn(Color.B, id = "bp7")
        val BP8 = Pawn(Color.B, id = "bp8")
        val BR1 = Rook(Color.B, id = "br1")
        val BR2 = Rook(Color.B, id = "br2")
        val BN1 = Knight(Color.B, id = "bn1")
        val BN2 = Knight(Color.B, id = "bn2")
        val BB1 = Bishop(Color.B, id = "bb1")
        val BB2 = Bishop(Color.B, id = "bb2")
        val BQ1 = Queen(Color.B, id = "bq1")
        val BK1 = King(Color.B, id = "bk1")
        val EM0 = EM

        val STARTING_BOARD: Array<Array<Piece>> =
            arrayOf(
                //     (a  , b  , c  , d  , e  , f  , g  , h  ),
                //     (0  , 1  , 2  , 3  , 4  , 5  , 6  , 7  ),
                arrayOf(WR1, WN1, WB1, WK1, WQ1, WB2, WN2, WR2), // 1 | 0
                arrayOf(WP1, WP2, WP3, WP4, WP5, WP6, WP7, WP8), // 2 | 1
                arrayOf(EM0, EM0, EM0, EM0, EM0, EM0, EM0, EM0), // 3 | 2
                arrayOf(EM0, EM0, EM0, EM0, EM0, EM0, EM0, EM0), // 4 | 3
                arrayOf(EM0, EM0, EM0, EM0, EM0, EM0, EM0, EM0), // 5 | 4
                arrayOf(EM0, EM0, EM0, EM0, EM0, EM0, EM0, EM0), // 6 | 5
                arrayOf(BP1, BP2, BP3, BP4, BP5, BP6, BP7, BP8), // 7 | 6
                arrayOf(BR1, BN1, BB1, BK1, BQ1, BB2, BN2, BR2), // 8 | 7
                //     (h  , g  , f  , e  , d  , c  , b  , a  ),
                //     (0  , 1  , 2  , 3  , 4  , 5  , 6  , 7  ),
            )

        private fun initBoard() = STARTING_BOARD.map { row ->
            row.map { it.clone() }.toTypedArray()
        }.toTypedArray()

        val DIRECTIONS = listOf(
            XY(0, -1),   // North
            XY(1, -1),   // North-East
            XY(1, 0),    // East
            XY(1, 1),    // South-East
            XY(0, 1),    // South
            XY(-1, 1),   // South-West
            XY(-1, 0),   // West
            XY(-1, -1)   // North-West
        )
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