package TNB.Switch.DTO.response;

import TNB.Switch.entity.Offer;
import TNB.Switch.enums.OfferType;

import java.util.UUID;

public record OfferSummaryResponse(UUID id, OfferType type, String label) {

    public static OfferSummaryResponse fromEntity(Offer offer) {
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