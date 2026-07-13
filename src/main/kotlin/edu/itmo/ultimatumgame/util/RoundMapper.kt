package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.model.Round
import org.mapstruct.AfterMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
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
