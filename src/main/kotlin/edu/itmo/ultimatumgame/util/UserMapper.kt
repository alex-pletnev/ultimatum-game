package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.UserResponse
import edu.itmo.ultimatumgame.model.User
import org.mapstruct.*

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
abstract class UserMapper {

    abstract fun toEntity(userResponse: UserResponse): User

    abstract fun toDto(user: User): UserResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(userResponse: UserResponse, @MappingTarget user: User): User
}
