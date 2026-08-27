package TNB.Switch.mapper;

import TNB.Switch.DTO.response.NotificationResponse;
import TNB.Switch.entity.Notification;

import java.util.function.Function;

public class NotificationMapper implements Function<Notification, NotificationResponse> {

    @Override
    public NotificationResponse apply(Notification notification) {
        if (notification == null) {
            return null;
        }

        return new NotificationResponse(
                notification.getId(),
                notification.getRecipientType(),
                notification.getChannel(),
                notification.getContent(),
                notification.isSent(),
                notification.getCreatedAt()
        );
    }
}