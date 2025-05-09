package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.dto.requests.CreateOfferCmd
import edu.itmo.ultimatum_game.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatum_game.model.Offer
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class]
)
abstract class OfferMapper {

    @Mapping(source = "amount", target = "offerValue")
    abstract fun toEntity(createOfferCmd: CreateOfferCmd): Offer

    abstract fun toDto(offer: Offer): OfferCreatedResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(offerCreatedResponse: OfferCreatedResponse, @MappingTarget offer: Offer): Offer
}