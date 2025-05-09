package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.requests.SessionConfigDto
import edu.itmo.ultimatum_game.dto.responses.SessionConfigResponse
import edu.itmo.ultimatum_game.model.SessionConfig
import org.mapstruct.*

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class SessionConfigMapper {

    abstract fun toEntity(sessionConfigDto: SessionConfigDto): SessionConfig

    abstract fun toDto(sessionConfig: SessionConfig): SessionConfigResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(
        sessionConfigDto: SessionConfigDto,
        @MappingTarget sessionConfig: SessionConfig
    ): SessionConfig
}