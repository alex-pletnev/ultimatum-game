@file:Suppress("UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.model

import java.util.UUID

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

        // Собираем карту userId -> teamId
        val userToTeam: Map<UUID, UUID> = session.teams
            .flatMap { team -> team.members.map { it.id!! to team.id!! } }
            .toMap()

        // Кандидаты-отвечающие
        val responders = session.members.toMutableList()
        check(responders.size == round.offers.size) {
            "Неверное состояние: участников и оферов должно быть равное число"
        }

        round.offers.forEach { offer ->
            val proposer = offer.proposer
                ?: error("offer.proposer не должен быть null")
            val proposerTeam = userToTeam[proposer.id]
                ?: error("Пользователь ${proposer.id} не состоит ни в одной команде")

            // Фильтруем тех, кто не в той же команде
            val valid = responders.filter { responder ->
                userToTeam[responder.id] != proposerTeam
            }
            check(valid.isNotEmpty()) {
                "Невозможно подобрать отвечающего из другой команды для ${proposer.id}"
            }

            // Случайный выбор и удаление из списка
            val chosen = valid.random()
            responders.remove(chosen)
            offer.responder = chosen
        }
    }
}
