package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.OfferPrewResponse
import edu.itmo.ultimatumgame.model.Offer
import org.mapstruct.*

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = [UserMapper::class, UserMapper::class]
)
abstract class OfferPrewMapper {

    abstract fun toDto(offer: Offer): OfferPrewResponse

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun partialUpdate(offerPrewResponse: OfferPrewResponse, @MappingTarget offer: Offer): Offer
}
