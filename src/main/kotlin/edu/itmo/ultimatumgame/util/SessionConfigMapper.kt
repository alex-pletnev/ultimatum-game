package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.requests.SessionConfigDto
import edu.itmo.ultimatumgame.dto.responses.SessionConfigResponse
import edu.itmo.ultimatumgame.model.SessionConfig
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt
abstract class SessionConfigMapper {

    abstract fun toEntity(sessionConfigDto: SessionConfigDto): SessionConfig

    abstract fun toDto(sessionConfig: SessionConfig): SessionConfigResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(
        sessionConfigDto: SessionConfigDto,
        @MappingTarget sessionConfig: SessionConfig
    ): SessionConfig
}
