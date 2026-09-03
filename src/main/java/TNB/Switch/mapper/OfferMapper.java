package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OfferResponse;
import TNB.Switch.entity.Offer;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class OfferMapper implements Function<Offer, OfferResponse> {

    @Override
    public OfferResponse apply(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getType(),
                offer.getLabel(),
                offer.getPrice(),
                offer.getCreditAmount(),
                offer.getDataVolumeMb(),
                offer.getDataValidityDays(),
                offer.getExchangeRate(),
                offer.getMinAmount(),
                offer.getMaxAmount(),
                offer.getOfferFeePercentage(),
                offer.isActive()
                // ❌ Plus de sourceOperatorId, destinationOperatorId
        );
    }
}