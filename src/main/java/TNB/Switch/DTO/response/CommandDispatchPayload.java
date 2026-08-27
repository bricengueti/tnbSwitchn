package TNB.Switch.DTO.response;

import java.util.UUID;

public record CommandDispatchPayload(
        UUID commandeId,
        String content
) {}
