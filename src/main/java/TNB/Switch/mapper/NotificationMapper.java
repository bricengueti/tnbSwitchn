package TNB.Switch.mapper;

import TNB.Switch.DTO.response.NotificationResponse;
import TNB.Switch.entity.Notification;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
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