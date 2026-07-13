package edu.itmo.ultimatumgame.model

import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.team
import edu.itmo.ultimatumgame.TestFixtures.user
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TeamBattleStrategyTest {

    private val strategy = TeamBattleStrategy()

    @Test
    fun `happy path — 2 команды по 2 игрока, responder всегда из чужой команды`() {
        val a1 = user(nickname = "A1")
        val a2 = user(nickname = "A2")
        val b1 = user(nickname = "B1")
        val b2 = user(nickname = "B2")
        val teamA = team(name = "A", members = mutableSetOf(a1, a2))
        val teamB = team(name = "B", members = mutableSetOf(b1, b2))
        val members = mutableSetOf<User>(a1, a2, b1, b2)
        val round = round(
            offers = mutableListOf(
                offer(proposer = a1),
                offer(proposer = a2),
                offer(proposer = b1),
                offer(proposer = b2),
            )
        )
        val session = session(
            members = members,
            teams = mutableSetOf(teamA, teamB),
            currentRound = round,
        )

        strategy.shuffleOffers(session)

        val teamAIds = teamA.members.map { it.id }.toSet()
        val teamBIds = teamB.members.map { it.id }.toSet()
        round.offers.forEach { o ->
            val proposerInA = o.proposer!!.id in teamAIds
            val responder = assertNotNull(o.responder)
            if (proposerInA) {
                assertTrue(responder.id in teamBIds, "responder ${responder.id} должен быть из команды B")
            } else {
                assertTrue(responder.id in teamAIds, "responder ${responder.id} должен быть из команды A")
            }
        }
        assertEquals(
            4,
            round.offers.mapNotNull { it.responder?.id }.toSet().size,
            "responder'ы должны быть уникальны — каждый игрок ровно раз"
        )
    }

    @Test
    fun `throws when currentRound is null`() {
        val session = session(members = mutableSetOf(user()), currentRound = null)
        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }

    @Test
    fun `throws when offers count не совпадает с числом members`() {
        val a1 = user()
        val a2 = user()
        val b1 = user()
        val teamA = team(members = mutableSetOf(a1, a2))
        val teamB = team(members = mutableSetOf(b1))
        val round = round(offers = mutableListOf(offer(proposer = a1))) // 1 offer, 3 members
        val session = session(
            members = mutableSetOf(a1, a2, b1),
            teams = mutableSetOf(teamA, teamB),
            currentRound = round,
        )
        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }

    @Test
    fun `throws when proposer не состоит ни в одной команде`() {
        val loner = user()
        val b1 = user()
        val teamB = team(members = mutableSetOf(b1))
        // loner не в команде, но в members
        val round = round(offers = mutableListOf(offer(proposer = loner), offer(proposer = b1)))
        val session = session(
            members = mutableSetOf(loner, b1),
            teams = mutableSetOf(teamB),
            currentRound = round,
        )
        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }

    @Test
    fun `throws when не осталось валидного responder из другой команды`() {
        // 1 игрок в команде A предлагает, но все остальные — тоже в A
        val a1 = user()
        val a2 = user()
        val teamA = team(members = mutableSetOf(a1, a2))
        val round = round(offers = mutableListOf(offer(proposer = a1), offer(proposer = a2)))
        val session = session(
            members = mutableSetOf(a1, a2),
            teams = mutableSetOf(teamA),
            currentRound = round,
        )
        assertThrows<IllegalStateException> { strategy.shuffleOffers(session) }
    }
}
