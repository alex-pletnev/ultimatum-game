package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.UserResponse
import edu.itmo.ultimatum_game.model.User
import org.mapstruct.*

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class UserMapper {

    abstract fun toEntity(userResponse: UserResponse): User

    abstract fun toDto(user: User): UserResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(userResponse: UserResponse, @MappingTarget user: User): User
}