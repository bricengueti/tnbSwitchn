package TNB.Switch.DTO.request;

import java.util.UUID;

/**
 * Résolution manuelle d'un message opérateur AMBIGUOUS/UNMATCHED :
 * l'admin tranche quelle Commande correspond réellement au message.
 */
public record ResolveManuallyRequest(
        UUID commandeId,
        boolean success
) {
}