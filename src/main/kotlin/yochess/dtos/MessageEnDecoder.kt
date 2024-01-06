package yochess.dtos

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.websocket.Decoder
import jakarta.websocket.Encoder
import jakarta.websocket.EndpointConfig
import java.io.Reader
import java.io.Writer

val objectMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

class MessageEnDecoder : Encoder.TextStream<Message>, Decoder.TextStream<Message> {
    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(move: Message, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(move))
    }

    override fun decode(reader: Reader): Message {
        return objectMapper.readValue(reader, Message::class.java)
    }
}