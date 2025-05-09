package edu.itmo.ultimatum_game.util


import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.model.Session
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [SessionConfigMapper::class, RoundPrewMapper::class, UserMapper::class, TeamMapper::class]
)
abstract class SessionWithTeamsAndMembersMapper {

    abstract fun toDto(session: Session): SessionWithTeamsAndMembersResponse
}

