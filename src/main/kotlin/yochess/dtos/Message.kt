package yochess.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Init(
    @JsonProperty("color")
    val color: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Move(
    @JsonProperty("piece")
    val piece: String,
    @JsonProperty("squareFrom")
    val squareFrom: String? = null,
    @JsonProperty("squareTo")
    val squareTo: String? = null,
    @JsonProperty("valid")
    val valid: Boolean? = null
)

//@JsonInclude(JsonInclude.Include.NON_NULL)
//@JsonIgnoreProperties(ignoreUnknown = true)
//data class MoveResponse(
//    @JsonProperty("message")
//    val piece: String,
//    @JsonProperty("squareFrom")
//    val squareFrom: String? = null,
//    @JsonProperty("squareTo")
//    val squareTo: String? = null,
//    @JsonProperty("valid")
//    val valid: Boolean = false
//)