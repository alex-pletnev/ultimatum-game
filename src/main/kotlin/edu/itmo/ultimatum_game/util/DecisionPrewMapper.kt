package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.DecisionPrewResponse
import edu.itmo.ultimatum_game.model.Decision
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class, OfferPrewMapper::class]
)
abstract class DecisionPrewMapper {

    abstract fun toEntity(decisionPrewResponse: DecisionPrewResponse): Decision

    abstract fun toDto(decision: Decision): DecisionPrewResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(decisionPrewResponse: DecisionPrewResponse, @MappingTarget decision: Decision): Decision
}