package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OfferSummaryResponse;
import TNB.Switch.entity.Offer;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class OfferSummaryMapper implements Function<Offer, OfferSummaryResponse> {

    @Override
    public OfferSummaryResponse apply(Offer offer) {
        if (offer == null) {
            return null;
        }

        return new OfferSummaryResponse(
                offer.getId(),
                offer.getType(),
                offer.getLabel()
        );
    }
}