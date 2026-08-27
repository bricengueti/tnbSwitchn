package TNB.Switch.DTO.request;

import java.util.UUID;

public record CommandAckPayload(UUID commandeId, boolean received) {}