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
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SmsSender smsSender;
    private final String adminTopicPrefix;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            SmsSender smsSender,
            @Value("${tnb.websocket.admin-topic-prefix}") String adminTopicPrefix) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.smsSender = smsSender;
        this.adminTopicPrefix = adminTopicPrefix;
    }

    /**
     * Alerte admin — broadcast à tous les admins connectés (pas de
     * destinataire précis, recipientId volontairement null : cf. CDC §7.8,
     * supervision partagée par toute l'équipe admin, pas un utilisateur
     * unique). Utilisé pour les 4 TODO identifiés : DLQ routage, DLQ
     * réconciliation, compensation épuisée, message AMBIGUOUS.
     */
    @Transactional
    public void alertAdmin(String content) {
        Notification notification = new Notification(
                NotificationRecipientType.ADMIN, null, NotificationChannel.STOMP, content
        );
        Notification saved = notificationRepository.save(notification);

        try {
            messagingTemplate.convertAndSend(adminTopicPrefix + "/alerts", content);
            saved.markSent();
            log.info("Alerte admin envoyée [{}] : {}", saved.getId(), content);
        } catch (Exception e) {
            // Échec d'envoi STOMP (ex. aucun admin connecté) : la
            // notification reste en base (sent=false), consultable plus
            // tard dans l'historique admin — pas d'exception remontée,
            // une alerte manquée ne doit jamais faire échouer le
            // traitement métier qui l'a déclenchée.
            log.warn("Échec d'envoi de l'alerte admin [{}] : {}", saved.getId(), e.getMessage());
        }
    }

    /**
     * Notification SMS au client (ex. confirmation de transaction,
     * échec nécessitant son attention). Distinct de l'OTP (géré par
     * AuthService directement) — ici pour les notifications de statut.
     */
    // Signature corrigée
    @Transactional
    public void notifyClient(User user, String content) {
        Notification notification = new Notification(
                NotificationRecipientType.USER, user.getId(), NotificationChannel.SMS, content
        );
        Notification saved = notificationRepository.save(notification);

        try {
            smsSender.send(user.getPhoneNumber(), content);
            saved.markSent();
            log.info("Notification SMS envoyée [{}] au client [{}]", saved.getId(), user.getId());
        } catch (Exception e) {
            log.warn("Échec d'envoi SMS [{}] : {}", saved.getId(), e.getMessage());
        }
    }

    /**
     * Notification temps réel au client via STOMP (popup de confirmation
     * retrait/dépôt, CDC §7.5) — canal distinct du SMS, pour l'app
     * mobile/web connectée.
     */
    public void notifyClientRealtime(UUID userId, String clientTopicPrefix, String content) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(), clientTopicPrefix + "/status", content
        );
        log.info("Notification temps réel envoyée au client [{}]", userId);
    }
}