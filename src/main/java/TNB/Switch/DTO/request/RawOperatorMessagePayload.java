package TNB.Switch.DTO.request;


import java.util.UUID;

public record RawOperatorMessagePayload(UUID operateurId, String rawContent) {}