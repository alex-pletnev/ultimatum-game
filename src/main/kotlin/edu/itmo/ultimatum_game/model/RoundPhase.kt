package edu.itmo.ultimatum_game.model

enum class RoundPhase {
    CREATED,
    OFFER_FORMS_SENTED,
    WAIT_OFFERS,
    ALL_OFFERS_RECEIVED,

    //shuffle

    OFFERS_SENTED,
    WAIT_DECISIONS,
    ALL_DECISIONS_RECEIVED,
    FINISHED,

}