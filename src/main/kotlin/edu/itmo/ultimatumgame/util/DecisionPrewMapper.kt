package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.DecisionPrewResponse
import edu.itmo.ultimatumgame.model.Decision
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class, OfferPrewMapper::class]
)
abstract class DecisionPrewMapper {

    abstract fun toEntity(decisionPrewResponse: DecisionPrewResponse): Decision

    abstract fun toDto(decision: Decision): DecisionPrewResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(decisionPrewResponse: DecisionPrewResponse, @MappingTarget decision: Decision): Decision
}
