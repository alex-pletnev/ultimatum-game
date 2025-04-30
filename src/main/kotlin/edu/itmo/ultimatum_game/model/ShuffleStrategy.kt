package edu.itmo.ultimatum_game.model

interface ShuffleStrategy {
    fun shuffleOffers(offers: List<Offer>, users: List<User>): List<Pair<User, Offer>>
}

class FreeForAllStrategy : ShuffleStrategy {

    override fun shuffleOffers(offers: List<Offer>, users: List<User>): List<Pair<User, Offer>> {
        TODO("Not yet implemented")
    }
}

class TeamBattleStrategy : ShuffleStrategy {

    override fun shuffleOffers(offers: List<Offer>, users: List<User>): List<Pair<User, Offer>> {
        TODO("Not yet implemented")
    }
}

