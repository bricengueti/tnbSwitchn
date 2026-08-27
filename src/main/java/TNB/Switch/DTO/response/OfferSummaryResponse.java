package TNB.Switch.DTO.response;


import TNB.Switch.enums.OfferType;

import java.util.UUID;

public record OfferSummaryResponse(UUID id, OfferType type, String label) {}