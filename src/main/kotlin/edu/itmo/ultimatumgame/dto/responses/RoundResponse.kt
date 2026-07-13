package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.RoundPhase
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Round}
 *
 * `myRole` и `myPendingActions` — enrichment hints для фронта (T-053). Вычисляются
 * относительно вызывающего user'а. В STOMP-broadcast остаются `NONE`/`emptyList()`,
 * потому что shared payload не привязан к конкретному пользователю.
 */
data class RoundResponse(
    val id: UUID,
    val roundNumber: Int,
    val roundPhase: RoundPhase,
    val offers: MutableList<OfferPrewResponse>,
    val decisions: MutableList<DecisionPrewResponse>,
    val session: SessionPrewResponse,
    val myRole: MyRole = MyRole.NONE,
    val myPendingActions: List<PendingAction> = emptyList(),
)

enum class MyRole {
    PROPOSER,
    RESPONDER,
    BOTH,
    NONE,
}

enum class PendingActionType {
    SEND_OFFER,
    MAKE_DECISION,
}

data class PendingAction(
    val type: PendingActionType,
    val offerId: UUID? = null, // заполнен только для MAKE_DECISION
)
