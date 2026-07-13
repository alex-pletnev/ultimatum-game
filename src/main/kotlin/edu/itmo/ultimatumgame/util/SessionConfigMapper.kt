package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.requests.SessionConfigDto
import edu.itmo.ultimatumgame.dto.responses.SessionConfigResponse
import edu.itmo.ultimatumgame.model.SessionConfig
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
