package TNB.Switch.DTO.request;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Création d'une transaction — l'utilisateur est déduit du principal
 * authentifié (TnbPrincipal), jamais fourni dans le body par le client
 * pour éviter qu'un client crée une transaction au nom d'un autre.
 *
 * - destinationPhoneNumber : requis UNIQUEMENT pour les offres EXCHANGE_MO
 *   (numéro du wallet de destination)
 * - payerPhoneNumber : requis UNIQUEMENT pour les offres EXCHANGE_MO
 *   (numéro du wallet source du client)
 */
public record CreateTransactionRequest(
        UUID offerId,
        BigDecimal amount,
        String destinationPhoneNumber,
        String payerPhoneNumber
) {}