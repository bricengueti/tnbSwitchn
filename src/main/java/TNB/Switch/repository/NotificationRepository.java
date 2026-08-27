package TNB.Switch.repository;

import TNB.Switch.entity.Notification;
import TNB.Switch.enums.NotificationRecipientType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientTypeAndRecipientIdAndSentFalse(
            NotificationRecipientType recipientType, UUID recipientId
    );

    // Notifications non envoyées, tous destinataires — pour un job de retry.
    List<Notification> findBySentFalse();
}