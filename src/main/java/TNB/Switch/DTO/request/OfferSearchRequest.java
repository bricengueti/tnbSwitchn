package TNB.Switch.DTO.request;

import TNB.Switch.enums.OfferType;

public record OfferSearchRequest(OfferType type, Boolean active) {}