package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.TestFixtures
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.User
import java.util.Random

object NpcTestFactories {

    fun offerCtx(
        roundSum: Int = 100,
        myPastRounds: List<RoundOutcome> = emptyList(),
        random: Random = Random(0),
        me: User = TestFixtures.user(),
        session: Session = TestFixtures.session(config = TestFixtures.sessionConfig(roundSum = roundSum)),
        round: Round = TestFixtures.round(session = session),
    ): OfferCtx = OfferCtx(session, round, me, myPastRounds, random)

    fun decisionCtx(
        roundSum: Int = 100,
        incomingAmount: Int,
        myPastRounds: List<RoundOutcome> = emptyList(),
        random: Random = Random(0),
        me: User = TestFixtures.user(),
        proposer: User = TestFixtures.user(),
        session: Session = TestFixtures.session(config = TestFixtures.sessionConfig(roundSum = roundSum)),
        round: Round = TestFixtures.round(session = session),
        incomingOffer: Offer = TestFixtures.offer(
            proposer = proposer,
            responder = me,
            session = session,
            round = round,
            offerValue = incomingAmount,
        ),
    ): DecisionCtx = DecisionCtx(session, round, me, incomingOffer, myPastRounds, random)
}
