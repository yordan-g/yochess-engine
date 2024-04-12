package yochess

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.mockito.InjectSpy
import jakarta.websocket.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import yochess.WebSocketResourceTests.Companion.EXCHANGED_MESSAGES
import yochess.dtos.Init
import yochess.dtos.Message
import yochess.dtos.MessageEnDecoder
import yochess.services.GamePhase
import yochess.services.GamesManager
import java.net.URI
import java.util.concurrent.*


@QuarkusTest
class WebSocketResourceTests {
    companion object {
        val EXCHANGED_MESSAGES = LinkedBlockingDeque<Message>()
    }

    @InjectSpy
    lateinit var gamesManager: GamesManager

    @TestHTTPResource("/chess")
    lateinit var baseWsEndpointUrl: URI

    @Test
    fun `GIVEN single user tries to connect WHEN he is the first player THEN connection succeeds AND the returned ws message should be INIT GamePhase INIT`() {
        val wsEndpoint = ContainerProvider.getWebSocketContainer()
        val session = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player1"))

        try {
            EXCHANGED_MESSAGES.poll(10, TimeUnit.SECONDS).run {
                shouldBeInstanceOf<Init>()
                color shouldBe "w"
                type shouldBe GamePhase.INIT
            }

            gamesManager.getWaitingPlayers().size shouldBe 1
            gamesManager.getActiveGames().size shouldBe 0

            Mockito.verify(gamesManager, times(1)).connectToRandomGame(any(), any())
        } finally {
            session.close()
        }

    }

    @Test
    fun `GIVEN 2 users try to connect THEN connection succeeds AND the second returned ws message should be INIT GamePhase START`() {
        val wsEndpoint = ContainerProvider.getWebSocketContainer()
        val session1 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player1"))
        // Await for the 1st ws connection to complete and allow messages to be exchanged in time.
        Thread.sleep(1000)
        val session2 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player2"))

        try {
            EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS).also { message1 ->
                message1.shouldBeInstanceOf<Init>()
                message1.color shouldBe "w"
                message1.type shouldBe GamePhase.INIT
            }
            EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS).also { message2 ->
                message2.shouldBeInstanceOf<Init>()
                message2.color shouldBe "w"
                message2.type shouldBe GamePhase.START
            }

            gamesManager.getWaitingPlayers().size shouldBe 0
            gamesManager.getActiveGames().size shouldBe 1

            Mockito.verify(gamesManager, times(2)).connectToRandomGame(any(), any())
        } finally {
            session1.close()
            session2.close()
        }
    }
}

@ClientEndpoint(
    encoders = [MessageEnDecoder::class],
    decoders = [MessageEnDecoder::class]
)
class Client {
    @OnOpen
    fun open(session: Session) {
    }

    @OnMessage
    fun message(msg: Message) {
        EXCHANGED_MESSAGES.add(msg)
    }
}
