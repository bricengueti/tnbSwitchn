package TNB.Switch.resolver;

import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Résout les placeholders d'un CommandTemplate avec les valeurs réelles
 * d'une Transaction et d'un FleetBalance (device assigné).
 *
 * Placeholders supportés :
 * - {phoneNumber}      : numéro du client (source du retrait)
 * - {amount}           : montant de la transaction
 * - {destinationPhoneNumber} : wallet destination (EXCHANGE_MO uniquement)
 * - {payerPhoneNumber} : wallet source (EXCHANGE_MO uniquement)
 * - {commercialNumber} : numéro commercial du device/opérateur (résolu au routage)
 *
 * Syntaxe : accolades simples { } — faciles à manipuler côté front.
 */
@Component
public class CommandTemplateResolver {

    /**
     * Résout un template avec les informations de la transaction.
     * Utilisé pour le retrait (pas de device assigné encore).
     */
    public String resolve(CommandTemplate template,
                          Transaction transaction,
                          String destinationPhoneNumber,
                          String payerPhoneNumber) {
        if (template == null) {
            throw new IllegalArgumentException("Le gabarit de commande ne peut pas être null");
        }

        String content = template.getContent();

        // Placeholders standard
        content = content.replace("{phoneNumber}", transaction.getClient().getPhoneNumber());
        content = content.replace("{amount}", transaction.getAmount().toPlainString());

        // Placeholder destination (EXCHANGE_MO)
        if (content.contains("{destinationPhoneNumber}")) {
            if (destinationPhoneNumber == null || destinationPhoneNumber.isBlank()) {
                throw new IllegalStateException(
                        "Le template requiert {destinationPhoneNumber} mais aucune destination n'a été fournie"
                );
            }
            content = content.replace("{destinationPhoneNumber}", destinationPhoneNumber);
        }

        // Placeholder payer (EXCHANGE_MO)
        if (content.contains("{payerPhoneNumber}")) {
            if (payerPhoneNumber == null || payerPhoneNumber.isBlank()) {
                throw new IllegalStateException(
                        "Le template requiert {payerPhoneNumber} mais aucun numéro source n'a été fourni"
                );
            }
            content = content.replace("{payerPhoneNumber}", payerPhoneNumber);
        }

        // {commercialNumber} est résolu au moment du routage (RoutingService)
        // car il dépend du device/FleetBalance assigné
        // On le laisse tel quel dans le contenu

        return content;
    }

    /**
     * Résout un template avec les informations de la transaction ET du device.
     * Utilisé juste avant l'envoi au device, une fois le FleetBalance connu.
     */
    public String resolveWithDevice(CommandTemplate template,
                                    Transaction transaction,
                                    String destinationPhoneNumber,
                                    String payerPhoneNumber,
                                    FleetBalance fleetBalance) {
        String content = resolve(template, transaction, destinationPhoneNumber, payerPhoneNumber);

        // Résoudre le numéro commercial depuis le FleetBalance
        if (content.contains("{commercialNumber}")) {
            if (fleetBalance == null || fleetBalance.getCommercialNumber() == null) {
                throw new IllegalStateException(
                        "Le template requiert {commercialNumber} mais aucun FleetBalance/numéro commercial n'a été fourni"
                );
            }
            content = content.replace("{commercialNumber}", fleetBalance.getCommercialNumber());
        }

        return content;
    }
}