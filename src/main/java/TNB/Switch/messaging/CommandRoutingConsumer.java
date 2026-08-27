package TNB.Switch.messaging;

import TNB.Switch.entity.Commande;
import TNB.Switch.exeption.NoAvailableDeviceException;
import TNB.Switch.repository.CommandeRepository;
import TNB.Switch.service.NotificationService;
import TNB.Switch.service.RoutingService;
import TNB.Switch.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * L'agent d'accueil qui reçoit les tickets dès qu'ils arrivent (au lieu de
 * faire des rondes). Si aucun guichetier n'est libre au moment du ticket
 * (NoAvailableDeviceException), Spring Kafka le remet automatiquement
 * dans une file d'attente secondaire avec un petit délai — comme
 * redonner un ticket "repassez dans 30 secondes" plutôt que de faire
 * poireauter le client devant un guichet fermé.
 *
 * Après tnb.routing.max-retries tentatives infructueuses, le ticket part
 * définitivement dans le bac "réclamations" (DLQ / DLT) où un humain
 * (l'admin) devra intervenir manuellement — la commande reste alors
 * simplement sans device assigné, visible dans une file de supervision.
 */
@Component
public class CommandRoutingConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandRoutingConsumer.class);

    private final RoutingService routingService;
    private final CommandeRepository commandeRepository;
    private final TransactionService transactionService;

    private final NotificationService notificationService;

    public CommandRoutingConsumer(RoutingService routingService,
                                  CommandeRepository commandeRepository,
                                  TransactionService transactionService, NotificationService notificationService) {
        this.routingService = routingService;
        this.commandeRepository = commandeRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @RetryableTopic(
            attempts = "${tnb.routing.max-retries}",
            backoff = @org.springframework.retry.annotation.Backoff(
                    delayExpression = "${tnb.routing.retry-delay-seconds} * 1000"
            ),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            include = { NoAvailableDeviceException.class }
    )
    @KafkaListener(topics = "${tnb.routing.withdrawal-topic}")
    public void consumeWithdrawal(@Payload CommandRoutingEvent event, Acknowledgment ack) {
        handleRouting(event);
        ack.acknowledge();
    }

    @RetryableTopic(
            attempts = "${tnb.routing.max-retries}",
            backoff = @org.springframework.retry.annotation.Backoff(
                    delayExpression = "${tnb.routing.retry-delay-seconds} * 1000"
            ),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            include = { NoAvailableDeviceException.class }
    )
    @KafkaListener(topics = "${tnb.routing.execution-topic}")
    public void consumeExecution(@Payload CommandRoutingEvent event, Acknowledgment ack) {
        handleRouting(event);
        ack.acknowledge();
    }

    /**
     * Trouve un guichetier, verrouille (RoutingService), PUIS informe le
     * chef d'orchestre (TransactionService.markCommandRouted) que la
     * commande vient de trouver preneur — c'est ce qui fait avancer le
     * statut global de la transaction (QUEUE_* -> ASK_*/
    private void handleRouting(CommandRoutingEvent event) {
        Optional<Commande> commandeOpt = commandeRepository.findById(event.commandeId());

        if (commandeOpt.isEmpty()) {
            log.warn("Commande [{}] introuvable, ticket ignoré", event.commandeId());
            return;
        }

        Commande commande = commandeOpt.get();

        if (commande.getDevice() != null) {
            log.debug("Commande [{}] déjà routée, ticket ignoré", event.commandeId());
            return;
        }

        routingService.routeSingleCommand(commande);
        transactionService.markCommandRouted(commande);
    }

    /**
     * Bac "réclamations" — dernier arrêt après épuisement des tentatives.
     * La commande reste sans device (aucune écriture destructive ici) ;
     * elle devient visible dans la file de supervision admin, qui pourra
     * soit forcer un routage manuel, soit attendre qu'un device se libère
     * et relancer une publication manuellement.
     */
// Constructeur mis à jour avec notificationService en paramètre
    @DltHandler
    public void handleDlt(@Payload CommandRoutingEvent event,
                          @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic) {
        log.error("Commande [{}] envoyée en DLQ après épuisement des tentatives (topic origine: {})",
                event.commandeId(), originalTopic);

        notificationService.alertAdmin(
                "Commande %s : aucun device disponible après plusieurs tentatives (topic %s)"
                        .formatted(event.commandeId(), originalTopic)
        );
    }
}