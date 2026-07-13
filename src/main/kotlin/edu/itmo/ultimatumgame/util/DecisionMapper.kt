package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatumgame.model.Decision
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class, OfferMapper::class]
)
@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt
abstract class DecisionMapper {

    abstract fun toEntity(decisionMadeResponse: DecisionMadeResponse): Decision

    abstract fun toDto(decision: Decision): DecisionMadeResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(decisionMadeResponse: DecisionMadeResponse, @MappingTarget decision: Decision): Decision
}
