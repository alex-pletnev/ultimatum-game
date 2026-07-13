package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.requests.CreateOfferCmd
import edu.itmo.ultimatumgame.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatumgame.model.Offer
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy
import org.mapstruct.ReportingPolicy

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [RoundPrewMapper::class, UserMapper::class]
)
@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt
abstract class OfferMapper {

    @Mapping(source = "amount", target = "offerValue")
    abstract fun toEntity(createOfferCmd: CreateOfferCmd): Offer

    abstract fun toDto(offer: Offer): OfferCreatedResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(offerCreatedResponse: OfferCreatedResponse, @MappingTarget offer: Offer): Offer
}
