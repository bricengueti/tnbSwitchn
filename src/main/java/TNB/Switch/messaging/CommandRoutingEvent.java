package TNB.Switch.messaging;

import java.util.UUID;

/**
 * Le "ticket" envoyé sur Kafka. Volontairement minimal : seulement l'ID
 * de la commande — le consumer recharge l'entité complète depuis la base
 * au moment du traitement plutôt que de faire voyager tout l'objet sur
 * Kafka (évite les données périmées si la commande a changé entre la
 * publication et la consommation).
 */
public record CommandRoutingEvent(UUID commandeId) {}