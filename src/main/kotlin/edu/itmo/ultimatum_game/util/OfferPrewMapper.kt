package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.responses.OfferPrewResponse
import edu.itmo.ultimatum_game.model.Offer
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class, UserMapper::class]
)
abstract class OfferPrewMapper {

    abstract fun toDto(offer: Offer): OfferPrewResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(offerPrewResponse: OfferPrewResponse, @MappingTarget offer: Offer): Offer
}