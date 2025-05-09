package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.requests.CreateSessionRequest
import edu.itmo.ultimatum_game.dto.responses.SessionResponse
import edu.itmo.ultimatum_game.model.Session
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [SessionConfigMapper::class, RoundPrewMapper::class, UserMapper::class, TeamPrewMapper::class]
)
abstract class SessionMapper {

    abstract fun toEntity(createSessionRequest: CreateSessionRequest): Session

    abstract fun toDto(session: Session): SessionResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(createSessionRequest: CreateSessionRequest, @MappingTarget session: Session): Session
}