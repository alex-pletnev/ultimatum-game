package edu.itmo.ultimatumgame.model

import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.user
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FreeForAllTest {

    private val strategy = FreeForAllStrategy()

    @Test
    fun `happy path — четыре игрока, каждый offer получает уникального responder != proposer`() {
        val a = user(nickname = "A")
        val b = user(nickname = "B")
        val c = user(nickname = "C")
        val d = user(nickname = "D")
        val members = mutableSetOf(a, b, c, d)
        val round = round(
            offers = mutableListOf(
                offer(proposer = a),
                offer(proposer = b),
                offer(proposer = c),
                offer(proposer = d),
            )
        )
        val session = session(members = members, currentRound = round)

        strategy.shuffleOffers(session)

        val responders = round.offers.map { it.responder }
        assertEquals(4, responders.filterNotNull().size, "у каждого оффера должен быть responder")
        assertEquals(4, responders.toSet().size, "responder'ы должны быть уникальны")
        round.offers.forEach { o ->
            assertNotEquals(o.proposer?.id, o.responder?.id, "responder не может быть тем же, что proposer")
        }
    }

    @Test
    fun `throws when currentRound is null`() {
        val session = session(members = mutableSetOf(user()), currentRound = null)
        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }

    @Test
    fun `throws when responders count не совпадает с числом оферов`() {
        val a = user()
        val b = user()
        val members = mutableSetOf(a, b)
        val round = round(offers = mutableListOf(offer(proposer = a))) // 1 offer, 2 members
        val session = session(members = members, currentRound = round)

        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }

    @Test
    fun `двое игроков — взаимный обмен ролями`() {
        val a = user(nickname = "A")
        val b = user(nickname = "B")
        val members = mutableSetOf(a, b)
        val round = round(
            offers = mutableListOf(offer(proposer = a), offer(proposer = b))
        )
        val session = session(members = members, currentRound = round)

        strategy.shuffleOffers(session)

        val offerA = round.offers.first { it.proposer == a }
        val offerB = round.offers.first { it.proposer == b }
        assertEquals(b.id, offerA.responder?.id, "оффер от A должен пойти к B")
        assertEquals(a.id, offerB.responder?.id, "оффер от B должен пойти к A")
    }

    @Test
    fun `responder — участник session_members, не какой-то посторонний`() {
        val members = (1..4).map { user(nickname = "P$it") }.toMutableSet()
        val round = round(offers = members.map { offer(proposer = it) }.toMutableList())
        val session = session(members = members, currentRound = round)

        strategy.shuffleOffers(session)

        val memberIds = members.map { it.id }.toSet()
        round.offers.forEach { o ->
            assertNotNull(o.responder)
            assertTrue(o.responder!!.id in memberIds, "responder ${o.responder!!.id} должен быть из members")
        }
    }

    // T-063 regression: bounded-retry Fisher-Yates должен детерминированно
    // завершаться и всегда давать корректный derangement, а не крутиться в do-while.
    @RepeatedTest(100)
    fun `derangement детерминирован — за 100 подряд запусков ни один не зависает и не даёт коллизии`() {
        val members = (1..4).map { user(nickname = "P$it") }.toMutableSet()
        val round = round(offers = members.map { offer(proposer = it) }.toMutableList())
        val session = session(members = members, currentRound = round)

        strategy.shuffleOffers(session)

        round.offers.forEach { o ->
            assertNotNull(o.responder)
            assertNotEquals(o.proposer?.id, o.responder?.id)
        }
        assertEquals(round.offers.size, round.offers.map { it.responder?.id }.toSet().size)
    }

    @Test
    fun `n=1 — shuffleOffers падает с IllegalStateException (derangement невозможен)`() {
        val a = user()
        val members = mutableSetOf(a)
        val round = round(offers = mutableListOf(offer(proposer = a)))
        val session = session(members = members, currentRound = round)

        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }
}
