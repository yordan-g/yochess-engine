package yochess

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.websocket.Decoder
import jakarta.websocket.Encoder
import jakarta.websocket.EndpointConfig
import java.io.Reader
import java.io.Writer

class MessageEnDecoder : Encoder.TextStream<MoveRequest>, Decoder.TextStream<MoveRequest> {

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(moveRequest: MoveRequest, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(moveRequest))
    }

    override fun decode(reader: Reader): MoveRequest {
        return objectMapper.readValue(reader, MoveRequest::class.java)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class MoveRequest(
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