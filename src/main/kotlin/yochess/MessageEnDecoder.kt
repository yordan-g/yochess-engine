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
import java.time.LocalDateTime
import java.io.Reader
import java.io.Writer

class MessageEnDecoder : Encoder.TextStream<Message>, Decoder.TextStream<Message> {

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(message: Message, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(message))
    }

    override fun decode(reader: Reader): Message {
        return objectMapper.readValue(reader, Message::class.java)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    @JsonProperty("message")
    val message: String,
    @JsonProperty("sender")
    val sender: String? = null,
    @JsonProperty("sentAt")
    val sentAt: LocalDateTime? = LocalDateTime.now()
)