@file:Suppress("UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.model

import java.util.Set.copyOf
import java.util.UUID

interface ShuffleStrategy {
    fun shuffleOffers(session: Session)
}

class FreeForAllStrategy : ShuffleStrategy {

    override fun shuffleOffers(session: Session) {
        val round = session.currentRound ?: error("session.currentRound не должен быть null к этому моменту")
        val responders = copyOf(session.members).toMutableSet()
        check(responders.size == round.offers.size) {
            "Недопустимое состояние (количество игроков и оферов должно совпадать на этом этапе)"
        }
        round.offers.forEach {
            var responder: User
            do {
                responder = responders.random()
            } while (responder.id == it.proposer?.id)
            responders.remove(responder)
            it.responder = responder
        }
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
