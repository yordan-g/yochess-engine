package yochess

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.websocket.Decoder
import jakarta.websocket.Encoder
import jakarta.websocket.EndpointConfig
import yochess.dtos.Init
import yochess.dtos.Move
import java.io.Reader
import java.io.Writer

class MessageEnDecoder : Encoder.TextStream<Move>, Decoder.TextStream<Move> {

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(move: Move, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(move))
    }

    override fun decode(reader: Reader): Move {
        return objectMapper.readValue(reader, Move::class.java)
    }
}

class InitEnDecoder : Encoder.TextStream<Init>, Decoder.TextStream<Init> {

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    override fun init(config: EndpointConfig) {}

    override fun destroy() {}

    override fun encode(init: Init, writer: Writer) {
        writer.append(objectMapper.writeValueAsString(init))
    }

    override fun decode(reader: Reader): Init {
        return objectMapper.readValue(reader, Init::class.java)
    }
}