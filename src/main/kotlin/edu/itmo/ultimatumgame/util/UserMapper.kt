package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.UserResponse
import edu.itmo.ultimatumgame.model.User
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class UserMapper {

    abstract fun toEntity(userResponse: UserResponse): User

    abstract fun toDto(user: User): UserResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(userResponse: UserResponse, @MappingTarget user: User): User
}
