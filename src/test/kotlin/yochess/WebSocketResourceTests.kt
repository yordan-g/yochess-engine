package yochess

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.mockito.InjectSpy
import jakarta.websocket.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import yochess.WebSocketResourceTests.Companion.EXCHANGED_MESSAGES
import yochess.dtos.End
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

    @AfterEach
    fun cleanExchangesWsMessages() {
        EXCHANGED_MESSAGES.removeAll { true }
    }

    @Nested
    inner class RandomGameTests {
        @Test
        fun `GIVEN single user tries to connect WHEN he is the first player THEN connection succeeds AND the returned ws message should be INIT GamePhase INIT`() {
            val wsEndpoint = ContainerProvider.getWebSocketContainer()
            val client = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player1"))

            try {
                EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS).run {
                    shouldBeInstanceOf<Init>()
                    color shouldBe "w"
                    type shouldBe GamePhase.INIT
                }
                EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS) shouldBe null

                gamesManager.getWaitingPlayers().size shouldBe 1
                gamesManager.getActiveGames().size shouldBe 1

                verifyInvocationsForRandomGame(timesInvoked = 1)
            } finally {
                client.close()
            }
        }

        @Test
        fun `GIVEN 4 users try to connect and start 2 games THEN connection succeeds AND 3 messages per game are exchanged`() {
            val wsEndpoint = ContainerProvider.getWebSocketContainer()
            // Await between connections to allow messages to be exchanged in time and order.
            val client1 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player1"))
            Thread.sleep(1000)
            val client2 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player2"))
            Thread.sleep(1000)
            val client3 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player3"))
            Thread.sleep(1000)
            val client4 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player4"))

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
                EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS).also { message3 ->
                    message3.shouldBeInstanceOf<Init>()
                    message3.color shouldBe "b"
                    message3.type shouldBe GamePhase.START

                    gamesManager.getGame(message3.gameId).player1.userId shouldBe "player1"
                    gamesManager.getGame(message3.gameId).player2.userId shouldBe "player2"
                }
                // second game
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
                EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS).also { message3 ->
                    message3.shouldBeInstanceOf<Init>()
                    message3.color shouldBe "b"
                    message3.type shouldBe GamePhase.START

                    gamesManager.getGame(message3.gameId).player1.userId shouldBe "player3"
                    gamesManager.getGame(message3.gameId).player2.userId shouldBe "player4"
                }

                gamesManager.getWaitingPlayers().size shouldBe 0
                gamesManager.getActiveGames().size shouldBe 2

                verifyInvocationsForRandomGame(timesInvoked = 4)
            } finally {
                client1.close()
                client2.close()
                client3.close()
                client4.close()
            }
        }

        private fun verifyInvocationsForRandomGame(timesInvoked: Int) {
            Mockito.verify(gamesManager, times(timesInvoked)).connectToRandomGame(any(), any())
            Mockito.verify(gamesManager, times(0)).connectToCustomGame(any(), any(), any(), any())
            Mockito.verify(gamesManager, times(0)).connectToRematchGame(any(), any(), any())
        }
    }

    @Nested
    inner class RematchGameTests {

        @Test
        fun `GIVEN existing random game is played WHEN both players send request for rematch THEN they should receive new rematchGameId AND using it they should be connected to the new game`() {
            val wsEndpoint = ContainerProvider.getWebSocketContainer()
            // Start a random game
            val client1 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player1"))
            Thread.sleep(1000)
            val client2 = wsEndpoint.connectToServer(Client::class.java, URI("${baseWsEndpointUrl}/player2"))
            lateinit var client3: Session
            lateinit var client4: Session

            try {
                // Setup current random game
                val message1 = EXCHANGED_MESSAGES.poll(1, TimeUnit.SECONDS) as Init

                // Send messages from the client to End the current game and offer rematch.
                client2.basicRemote.sendObject(End(gameId = message1.gameId, ended = true))
                client1.basicRemote.sendObject(End(gameId = message1.gameId, rematch = true))
                client2.basicRemote.sendObject(End(gameId = message1.gameId, rematch = true))
                // Allow messages from above to complete
                Thread.sleep(1000)

                val responseRematchMessageFromServer = EXCHANGED_MESSAGES.last
                responseRematchMessageFromServer.shouldBeInstanceOf<End>()
                responseRematchMessageFromServer.gameId shouldBe message1.gameId
                responseRematchMessageFromServer.rematchSuccess shouldBe true
                responseRematchMessageFromServer.rematchGameId.shouldNotBeNull()

                // may close current game
                // start connecting to the rematch game
                client3 = wsEndpoint.connectToServer(
                    Client::class.java,
                    URI("${baseWsEndpointUrl}/player1?rematchGameId=${responseRematchMessageFromServer.rematchGameId}")
                )
                Thread.sleep(1000)
                client4 = wsEndpoint.connectToServer(
                    Client::class.java,
                    URI("${baseWsEndpointUrl}/player1?rematchGameId=${responseRematchMessageFromServer.rematchGameId}")
                )
                Thread.sleep(1000)

                // Assert that the last 3 messages were correct to start the rematch game
                EXCHANGED_MESSAGES.pollLast(1, TimeUnit.SECONDS).also { message1 ->
                    message1.shouldBeInstanceOf<Init>()
                    message1.type shouldBe GamePhase.START
                    message1.gameId shouldBe responseRematchMessageFromServer.rematchGameId
                }
                EXCHANGED_MESSAGES.pollLast(1, TimeUnit.SECONDS).also { message2 ->
                    message2.shouldBeInstanceOf<Init>()
                    message2.type shouldBe GamePhase.START
                    message2.gameId shouldBe responseRematchMessageFromServer.rematchGameId
                }
                EXCHANGED_MESSAGES.pollLast(1, TimeUnit.SECONDS).also { message3 ->
                    message3.shouldBeInstanceOf<Init>()
                    message3.type shouldBe GamePhase.INIT
                    message3.gameId shouldBe responseRematchMessageFromServer.rematchGameId
                }

                Mockito.verify(gamesManager, times(2)).connectToRematchGame(any(), any(), any())
            } finally {
                client1.close()
                client2.close()
                client3.close()
                client4.close()
            }
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
