package TNB.Switch.service;

import TNB.Switch.entity.Operateur;
import TNB.Switch.exeption.InvalidPhoneNumberException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhoneNumberValidationService {

    /**
     * Valide qu'un numéro appartient bien à l'opérateur.
     */
    public void validatePhoneNumberBelongsToOperateur(String phoneNumber, Operateur operateur) {
        if (operateur == null) {
            throw new InvalidPhoneNumberException("Opérateur non spécifié pour la validation");
        }

        if (!operateur.ownsPhoneNumber(phoneNumber)) {
            List<String> validPrefixes = operateur.getActivePrefixes();
            String prefixesStr = validPrefixes.isEmpty() ? "aucun" : String.join(", ", validPrefixes);

            throw new InvalidPhoneNumberException(
                    String.format("Le numéro %s n'appartient pas à l'opérateur %s (%s). " +
                                    "Préfixes valides: %s",
                            phoneNumber,
                            operateur.getNom(),
                            operateur.getCode(),
                            prefixesStr
                    )
            );
        }
    }

    /**
     * Valide les deux numéros d'une transaction.
     */
    public void validateTransactionPhoneNumbers(String payerPhoneNumber, String destinationPhoneNumber,
                                                Operateur fromOperateur, Operateur toOperateur) {
        // Valider le numéro de l'émetteur
        validatePhoneNumberBelongsToOperateur(payerPhoneNumber, fromOperateur);

        // Valider le numéro du destinataire
        validatePhoneNumberBelongsToOperateur(destinationPhoneNumber, toOperateur);

        // Vérifier que les deux numéros sont différents (pour EXCHANGE_MO)
        String normalizedPayer = normalizePhoneNumber(payerPhoneNumber);
        String normalizedDestination = normalizePhoneNumber(destinationPhoneNumber);
        if (normalizedPayer.equals(normalizedDestination)) {
            throw new InvalidPhoneNumberException(
                    "Le numéro de l'émetteur et du destinataire ne peuvent pas être identiques"
            );
        }
    }

    /**
     * Normalise un numéro de téléphone (enlève les caractères non numériques).
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}