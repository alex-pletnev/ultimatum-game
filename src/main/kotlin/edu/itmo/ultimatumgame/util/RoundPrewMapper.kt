package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.RoundPrewResponse
import edu.itmo.ultimatumgame.model.Round
import org.mapstruct.*

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class RoundPrewMapper {

    abstract fun toDto(round: Round): RoundPrewResponse

    abstract fun toEntity(roundPrewResponse: RoundPrewResponse): Round

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(roundPrewResponse: RoundPrewResponse, @MappingTarget round: Round): Round
}
