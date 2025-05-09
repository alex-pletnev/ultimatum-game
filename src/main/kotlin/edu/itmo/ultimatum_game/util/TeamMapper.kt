package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.TeamResponse
import edu.itmo.ultimatum_game.model.Team
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class]
)
abstract class TeamMapper {

    abstract fun toEntity(teamResponse: TeamResponse): Team

    abstract fun toDto(team: Team): TeamResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(teamResponse: TeamResponse, @MappingTarget team: Team): Team
}