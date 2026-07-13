package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.SessionPrewResponse
import edu.itmo.ultimatumgame.model.Session
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [SessionConfigMapper::class, UserMapper::class]
)
@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt
abstract class SessionPrewMapper {

    abstract fun toDto(session: Session): SessionPrewResponse
}
