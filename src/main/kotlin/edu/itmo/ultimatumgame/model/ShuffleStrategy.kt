@file:Suppress("UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.model

interface ShuffleStrategy {
    fun shuffleOffers(session: Session)
}

class FreeForAllStrategy : ShuffleStrategy {

    override fun shuffleOffers(session: Session) {
        val round = session.currentRound ?: error("session.currentRound не должен быть null к этому моменту")
        val respondersPool = session.members.toList()
        check(respondersPool.size == round.offers.size) {
            "Недопустимое состояние (количество игроков и оферов должно совпадать на этом этапе)"
        }
        check(respondersPool.size >= MIN_PLAYERS_FOR_DERANGEMENT) {
            "shuffleOffers требует ≥2 участников: derangement для n=1 не существует"
        }
        // Bounded-retry Fisher-Yates. Expected attempts ~ e ≈ 2.71;
        // probability of 100 неудач ≈ (1-1/e)^100 ≈ 10^-20. См. T-063.
        val proposerIds = round.offers.map { it.proposer?.id }
        repeat(MAX_DERANGEMENT_ATTEMPTS) {
            val shuffled = respondersPool.shuffled()
            if (shuffled.indices.all { idx -> shuffled[idx].id != proposerIds[idx] }) {
                round.offers.forEachIndexed { i, o -> o.responder = shuffled[i] }
                return
            }
        }
        error("Не удалось построить derangement за $MAX_DERANGEMENT_ATTEMPTS попыток (RNG-аномалия?)")
    }

    private companion object {
        const val MIN_PLAYERS_FOR_DERANGEMENT = 2
        const val MAX_DERANGEMENT_ATTEMPTS = 100
    }
}

class TeamBattleStrategy : ShuffleStrategy {

    override fun shuffleOffers(session: Session) {
        val round = session.currentRound
            ?: error("session.currentRound не должен быть null к этому моменту")

        val groups = session.teams.filter { it.members.isNotEmpty() }.shuffled()
        val ordered = groups.flatMap { it.members.shuffled() }
        val largest = groups.maxOfOrNull { it.members.size } ?: 0
        check(ordered.size == session.members.size && ordered.toSet() == session.members) {
            "Каждый участник должен состоять ровно в одной команде"
        }
        check(largest > 0 && largest * 2 <= ordered.size) {
            "В одной команде не может быть больше половины всех участников"
        }
        check(round.offers.size == ordered.size && round.offers.map { it.proposer }.toSet() == session.members) {
            "Каждый участник должен сделать ровно одно предложение"
        }
        // Rotating contiguous team blocks by the largest block guarantees cross-team pairs.
        val assignments = ordered.indices.associate { index ->
            ordered[index].id to ordered[(index + largest) % ordered.size]
        }
        round.offers.forEach { it.responder = assignments.getValue(it.proposer!!.id) }
    }
}
