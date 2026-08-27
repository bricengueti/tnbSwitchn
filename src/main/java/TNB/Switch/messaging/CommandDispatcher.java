package TNB.Switch.messaging;
import TNB.Switch.DTO.response.CommandDispatchPayload;
import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Envoie une Commande routée au device qui vient de la recevoir, via le
 * canal STOMP dédié (tnb.websocket.device-topic-prefix). Utilise
 * convertAndSendToUser plutôt qu'un simple topic public : chaque device
 * ne doit recevoir QUE les commandes qui lui sont destinées, jamais
 * celles des autres devices connectés au même broker.
 */
@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final String deviceTopicPrefix;

    public CommandDispatcher(
            SimpMessagingTemplate messagingTemplate,
            @Value("${tnb.websocket.device-topic-prefix}") String deviceTopicPrefix) {
        this.messagingTemplate = messagingTemplate;
        this.deviceTopicPrefix = deviceTopicPrefix;
    }

    public void dispatch(Device device, Commande commande) {
        CommandDispatchPayload payload = new CommandDispatchPayload(
                commande.getId(), commande.getResolvedContent()
        );

        // convertAndSendToUser cible la session STOMP précise du device
        // (identifiée par son "user name" — ici l'UUID du device, tel
        // qu'injecté dans accessor.setUser() au CONNECT par
        // DeviceStompAuthInterceptor). Le device s'abonne côté client à
        // /user/{deviceTopicPrefix}/commands pour recevoir ses commandes.
        messagingTemplate.convertAndSendToUser(
                device.getId().toString(),
                deviceTopicPrefix + "/commands",
                payload
        );

        log.info("Commande [{}] envoyée au device [{}]", commande.getId(), device.getId());
    }
}