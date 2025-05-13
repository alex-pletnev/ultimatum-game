package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.RoundResponse
import edu.itmo.ultimatum_game.model.Round
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [OfferPrewMapper::class, DecisionPrewMapper::class, SessionPrewMapper::class]
)
abstract class RoundMapper {
    @AfterMapping
    fun linkOffers(@MappingTarget round: Round) {
        round.offers.forEach { it.round = round }
    }

    @AfterMapping
    fun linkDecisions(@MappingTarget round: Round) {
        round.decisions.forEach { it.round = round }
    }

    abstract fun toEntity(roundResponse: RoundResponse): Round

    abstract fun toDto(round: Round): RoundResponse

}