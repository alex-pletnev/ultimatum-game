package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.requests.CreateOfferCmd
import edu.itmo.ultimatumgame.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatumgame.model.Offer
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class]
)
abstract class OfferMapper {

    @Mapping(source = "amount", target = "offerValue")
    abstract fun toEntity(createOfferCmd: CreateOfferCmd): Offer

    abstract fun toDto(offer: Offer): OfferCreatedResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(offerCreatedResponse: OfferCreatedResponse, @MappingTarget offer: Offer): Offer
}
