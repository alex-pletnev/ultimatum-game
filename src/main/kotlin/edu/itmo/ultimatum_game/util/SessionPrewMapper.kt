package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.SessionPrewResponse
import edu.itmo.ultimatum_game.model.Session
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [SessionConfigMapper::class, UserMapper::class]
)
abstract class SessionPrewMapper {

    abstract fun toDto(session: Session): SessionPrewResponse

}