package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Offer
import org.springframework.data.repository.CrudRepository
import java.util.*

interface OfferRepository : CrudRepository<Offer, UUID>