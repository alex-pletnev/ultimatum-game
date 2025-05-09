package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatum_game.model.Decision
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class, OfferMapper::class]
)
abstract class DecisionMapper {

    abstract fun toEntity(decisionMadeResponse: DecisionMadeResponse): Decision

    abstract fun toDto(decision: Decision): DecisionMadeResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(decisionMadeResponse: DecisionMadeResponse, @MappingTarget decision: Decision): Decision
}