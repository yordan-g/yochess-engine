package yochess.dtos

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import yochess.dtos.Time.Companion.DEFAULT_TIME_LEFT
import yochess.services.GamePhase

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Init::class, name = "INIT"),
    JsonSubTypes.Type(value = Move::class, name = "MOVE"),
    JsonSubTypes.Type(value = End::class, name = "END"),
    JsonSubTypes.Type(value = ChangeName::class, name = "CHANGE_NAME"),
    JsonSubTypes.Type(value = CommunicationError::class, name = "COMMUNICATION_ERROR"),
)
sealed class Message

data class Init(
    val kind: String = "INIT",
    val type: GamePhase = GamePhase.INIT,
    val color: String? = null,
    val gameId: String
) : Message()

data class Move(
    val kind: String = "MOVE",
    val piece: String,
    val squareFrom: String,
    val squareTo: String,
    val gameId: String,
    var valid: Boolean = false,
    val position: String? = null,
    val enPassantCapturePos: String? = null,
    val promotion: String? = null,
    val castle: Castle? = null,
    val end: String? = null,
    var whiteCaptures: List<String> = listOf(),
    var blackCaptures: List<String> = listOf(),
    var timeLeft: Time = DEFAULT_TIME_LEFT,
    var turn: String = "w"
) : Message()

data class End(
    val kind: String = "END",
    val gameId: String,
    var timeout: Boolean? = null,
    val ended: Boolean? = null,
    val leftGame: Boolean? = null,
    val close: Boolean? = null,
    val rematch: Boolean? = null,
    val rematchSuccess: Boolean? = null,
    val rematchGameId: String? = null,
    var gameOver: GameOver? = null
) : Message()

data class ChangeName(
    val kind: String = "CHANGE_NAME",
    val gameId: String,
    val name: String
): Message()

data class CommunicationError(
    val kind: String = "COMMUNICATION_ERROR",
    val isPresent: Boolean = true,
    val userMessage: String
): Message()

data class Castle(
    val rook: String,
    val rookPosStart: String,
    val rookPosEnd: String,
)

data class Time(
    val white: Long,
    val black: Long,
) {
    companion object {
        val DEFAULT_TIME_LEFT = Time(500L, 500L)
    }
}

data class GameOver(
    val winner: String,
    val result: String,
)