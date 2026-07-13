package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.TeamResponse
import edu.itmo.ultimatumgame.model.Team
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class]
)
abstract class TeamMapper {

    abstract fun toEntity(teamResponse: TeamResponse): Team

    abstract fun toDto(team: Team): TeamResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(teamResponse: TeamResponse, @MappingTarget team: Team): Team
}
