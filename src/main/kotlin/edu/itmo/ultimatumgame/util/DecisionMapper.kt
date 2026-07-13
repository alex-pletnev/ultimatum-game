package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatumgame.model.Decision
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class, OfferMapper::class]
)
abstract class DecisionMapper {

    abstract fun toEntity(decisionMadeResponse: DecisionMadeResponse): Decision

    abstract fun toDto(decision: Decision): DecisionMadeResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(decisionMadeResponse: DecisionMadeResponse, @MappingTarget decision: Decision): Decision
}
