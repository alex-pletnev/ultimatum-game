package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.TeamPrewResponse
import edu.itmo.ultimatumgame.model.Team
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class TeamPrewMapper {

    abstract fun toDto(round: Team): TeamPrewResponse
}
