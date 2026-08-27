package TNB.Switch.entity;

import TNB.Switch.enums.NotificationChannel;
import TNB.Switch.enums.NotificationRecipientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification extends BaseLedgerEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, updatable = false, length = 10)
    private NotificationRecipientType recipientType;

    // Pas de FK stricte vers User/Admin : un destinataire ADMIN peut viser
    // "tous les admins connectés" (broadcast supervision), pas un id précis.
    @Column(name = "recipient_id", updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "content", nullable = false, updatable = false, length = 500)
    private String content;

    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    protected Notification() {
        // requis par JPA
    }

    public Notification(NotificationRecipientType recipientType, UUID recipientId,
                        NotificationChannel channel, String content) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.channel = channel;
        this.content = content;
    }

    public NotificationRecipientType getRecipientType() { return recipientType; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationChannel getChannel() { return channel; }
    public String getContent() { return content; }
    public boolean isSent() { return sent; }
    public void markSent() { this.sent = true; }
}