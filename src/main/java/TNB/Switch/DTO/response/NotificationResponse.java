package TNB.Switch.DTO.response;


import TNB.Switch.enums.NotificationChannel;
import TNB.Switch.enums.NotificationRecipientType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationRecipientType recipientType,
        NotificationChannel channel,
        String content,
        boolean sent,
        Instant createdAt
) {}