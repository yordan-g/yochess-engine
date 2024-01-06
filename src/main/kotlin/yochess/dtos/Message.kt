package yochess.dtos

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import yochess.dtos.Time.Companion.DEFAULT_TIME_LEFT
import yochess.services.WebSocketPhase

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Init::class, name = "INIT"),
    JsonSubTypes.Type(value = Move::class, name = "MOVE")
)
sealed class Message

data class Init(
    val kind: String = "INIT",
    val type: WebSocketPhase = WebSocketPhase.INIT,
    val color: String? = null,
    val gameId: String
) : Message()

data class Move(
    val kind: String = "MOVE",
    val piece: String,
    val squareFrom: String,
    val squareTo: String,
    val gameId: String,
    val valid: Boolean = false,
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