package yochess.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import yochess.services.WebSocketPhase

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class InitMessage(
    @JsonProperty("type")
    val type: WebSocketPhase = WebSocketPhase.INIT,
    @JsonProperty("color")
    val color: String? = null,
    @JsonProperty("gameId")
    val gameId: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Move(
    @JsonProperty("type")
    val type: String = "MOVE",
    @JsonProperty("piece")
    val piece: String,
    @JsonProperty("squareFrom")
    val squareFrom: String? = null,
    @JsonProperty("squareTo")
    val squareTo: String? = null,
    @JsonProperty("gameId")
    val gameId: String,
    @JsonProperty("valid")
    val valid: Boolean? = null
)