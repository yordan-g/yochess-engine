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

class MessageEnDecoder : Encoder.TextStream<Move>, Decoder.TextStream<Move> {
    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(move: Move, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(move))
    }

    override fun decode(reader: Reader): Move {
        return objectMapper.readValue(reader, Move::class.java)
    }
}

class InitEnDecoder : Encoder.TextStream<InitMessage>, Decoder.TextStream<InitMessage> {
    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(initMessage: InitMessage, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(initMessage))
    }

    override fun decode(reader: Reader): InitMessage {
        return objectMapper.readValue(reader, InitMessage::class.java)
    }
}