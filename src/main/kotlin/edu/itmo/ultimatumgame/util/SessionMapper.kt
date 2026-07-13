package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.requests.CreateSessionRequest
import edu.itmo.ultimatumgame.dto.responses.SessionResponse
import edu.itmo.ultimatumgame.model.Session
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [SessionConfigMapper::class, RoundPrewMapper::class, UserMapper::class, TeamPrewMapper::class]
)
@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt
abstract class SessionMapper {

    abstract fun toEntity(createSessionRequest: CreateSessionRequest): Session

    abstract fun toDto(session: Session): SessionResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(createSessionRequest: CreateSessionRequest, @MappingTarget session: Session): Session
}
