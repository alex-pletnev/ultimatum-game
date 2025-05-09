package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.TeamPrewResponse
import edu.itmo.ultimatum_game.model.Team
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class TeamPrewMapper {

    abstract fun toDto(round: Team): TeamPrewResponse
}