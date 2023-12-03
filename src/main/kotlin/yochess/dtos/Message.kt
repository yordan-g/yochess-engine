package yochess.dtos

import yochess.services.WebSocketPhase

data class InitMessage(
    val type: WebSocketPhase = WebSocketPhase.INIT,
    val color: String? = null,
    val gameId: String
)

data class Move(
    val type: String = "MOVE",
    val piece: String,
    val squareFrom: String,
    val squareTo: String,
    val gameId: String,
    val valid: Boolean? = null,
    val position: String? = null,
    val enPassantCapture: String? = null,
    val promotion: String? = null,
    val castle: Castle? = null,
    val end: String? = null
)

data class Castle(
    val rook: String,
    val rookPosStart: String,
    val rookPosEnd: String,
)