package TNB.Switch.service;

import TNB.Switch.entity.Notification;
import TNB.Switch.entity.User;
import TNB.Switch.enums.NotificationChannel;
import TNB.Switch.enums.NotificationRecipientType;
import TNB.Switch.impl.SmsSender;
import TNB.Switch.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Point d'entrée unique pour toute notification sortante. Persiste
 * systématiquement AVANT l'envoi (même logique que MessageOperateurBrut :
 * si l'envoi échoue, la trace reste en base pour un retry ultérieur,
 * rien n'est perdu). Deux canaux : STOMP pour l'admin (temps réel,
 * broadcast sur tnb.websocket.admin-topic-prefix), SMS pour le client.
 *
 * =====================================================================
 *                    NOTIFICATION SERVICE
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  ALERTE ADMIN (STOMP)                                             │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  alertAdmin(message)                                              │
 *  │  → Notification (ADMIN, STOMP, content)                          │
 *  │  → messagingTemplate.convertAndSend(/topic/admin/alerts)         │
 *  │  → Utilisé pour: DLQ routage, DLQ réconciliation, compensation   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  NOTIFICATION CLIENT (SMS)                                        │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  notifyClient(user, content)                                      │
 *  │  → Notification (USER, SMS, content)                             │
 *  │  → smsSender.send(phoneNumber, content)                          │
 *  │  → Utilisé pour: confirmation transaction, échec                │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  NOTIFICATION CLIENT TEMPS RÉEL (STOMP)                           │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  notifyClientRealtime(userId, content)                            │
 *  │  → messagingTemplate.convertAndSendToUser(userId, /queue/status)  │
 *  │  → Utilisé pour: popup confirmation retrait/dépôt                │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Envoi réussi (marked as sent)
 *    - - - = Échec d'envoi (reste sent=false → retry ultérieur)
 * =====================================================================
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SmsSender smsSender;
    private final String adminTopicPrefix;
    private final String clientTopicPrefix;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            SmsSender smsSender,
            @Value("${tnb.websocket.admin-topic-prefix:/topic/admin}") String adminTopicPrefix,
            @Value("${tnb.websocket.client-topic-prefix:/topic/client}") String clientTopicPrefix) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.smsSender = smsSender;
        this.adminTopicPrefix = adminTopicPrefix;
        this.clientTopicPrefix = clientTopicPrefix;
    }

    // =====================================================================
    //  ALERTE ADMIN (STOMP)
    // =====================================================================

    /**
     * Alerte admin — broadcast à tous les admins connectés.
     * Utilisé pour: DLQ routage, DLQ réconciliation, compensation épuisée,
     * message AMBIGUOUS, délais exceptionnels.
     */
    @Transactional("transactionManager")
    public void alertAdmin(String content) {
        Notification notification = new Notification(
                NotificationRecipientType.ADMIN,
                null,
                NotificationChannel.STOMP,
                content
        );
        Notification saved = notificationRepository.save(notification);

        try {
            messagingTemplate.convertAndSend(adminTopicPrefix + "/alerts", content);
            saved.markSent();
            notificationRepository.save(saved);
            log.info("Alerte admin envoyée [{}] : {}", saved.getId(), content);
        } catch (Exception e) {
            // Échec d'envoi STOMP (ex. aucun admin connecté) : la
            // notification reste en base (sent=false), consultable plus
            // tard dans l'historique admin — pas d'exception remontée.
            log.warn("Échec d'envoi de l'alerte admin [{}] : {}", saved.getId(), e.getMessage());
        }
    }

    // =====================================================================
    //  NOTIFICATION CLIENT (SMS)
    // =====================================================================

    /**
     * Notification SMS au client (ex. confirmation de transaction,
     * échec nécessitant son attention). Distinct de l'OTP (géré par
     * AuthService directement).
     */
    @Transactional("transactionManager")
    public void notifyClient(User user, String content) {
        Notification notification = new Notification(
                NotificationRecipientType.USER,
                user.getId(),
                NotificationChannel.SMS,
                content
        );
        Notification saved = notificationRepository.save(notification);

        try {
            smsSender.send(user.getPhoneNumber(), content);
            saved.markSent();
            notificationRepository.save(saved);
            log.info("Notification SMS envoyée [{}] au client [{}]", saved.getId(), user.getId());
        } catch (Exception e) {
            log.warn("Échec d'envoi SMS [{}] : {}", saved.getId(), e.getMessage());
        }
    }

    // =====================================================================
    //  NOTIFICATION CLIENT TEMPS RÉEL (STOMP)
    // =====================================================================

    /**
     * Notification temps réel au client via STOMP (popup de confirmation
     * retrait/dépôt, CDC §7.5) — canal distinct du SMS, pour l'app
     * mobile/web connectée.
     */
    public void notifyClientRealtime(UUID userId, String content) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                clientTopicPrefix + "/status",
                content
        );
        log.info("Notification temps réel envoyée au client [{}]", userId);
    }

    // =====================================================================
    //  NOTIFICATION CLIENT TEMPS RÉEL AVEC TOPIC PERSONNALISÉ
    // =====================================================================

    /**
     * Version avec topic personnalisé pour les différents types de notifications.
     */
    public void notifyClientRealtime(UUID userId, String topic, String content) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                topic,
                content
        );
        log.info("Notification temps réel [{}] envoyée au client [{}]", topic, userId);
    }

    // =====================================================================
    //  NOTIFICATION DE STATUT DE TRANSACTION
    // =====================================================================

    /**
     * Envoie une notification de statut de transaction au client.
     * Utilise à la fois SMS et STOMP selon la disponibilité du client.
     */
    @Transactional("transactionManager")
    public void notifyTransactionStatus(User user, String transactionId, String status, String message) {
        String content = "Transaction %s: %s - %s".formatted(transactionId, status, message);

        // 1. SMS
        notifyClient(user, content);

        // 2. STOMP (temps réel)
        notifyClientRealtime(user.getId(), clientTopicPrefix + "/transaction/" + transactionId, content);
    }
}