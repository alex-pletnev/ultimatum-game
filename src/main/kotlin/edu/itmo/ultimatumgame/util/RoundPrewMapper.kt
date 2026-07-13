package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.RoundPrewResponse
import edu.itmo.ultimatumgame.model.Round
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class RoundPrewMapper {

    abstract fun toDto(round: Round): RoundPrewResponse

    abstract fun toEntity(roundPrewResponse: RoundPrewResponse): Round

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(roundPrewResponse: RoundPrewResponse, @MappingTarget round: Round): Round
}
