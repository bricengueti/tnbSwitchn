package TNB.Switch.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Création d'une transaction — l'utilisateur est déduit du principal
 * authentifié (TnbPrincipal), jamais fourni dans le body par le client
 * pour éviter qu'un client crée une transaction au nom d'un autre.
 *
 * - fromOperateurId : opérateur source (ex: ORANGE)
 * - toOperateurId : opérateur destination (ex: MTN)
 * - destinationPhoneNumber : requis UNIQUEMENT pour les offres EXCHANGE_MO
 *   (numéro du wallet de destination)
 * - payerPhoneNumber : requis UNIQUEMENT pour les offres EXCHANGE_MO
 *   (numéro du wallet source du client)
 */
public record CreateTransactionRequest(
        @NotNull(message = "L'ID de l'offre est obligatoire")
        UUID offerId,

        @NotNull(message = "L'ID de l'opérateur source est obligatoire")
        UUID fromOperateurId,

        @NotNull(message = "L'ID de l'opérateur destination est obligatoire")
        UUID toOperateurId,

        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être positif")
        BigDecimal amount,

        String destinationPhoneNumber,

        String payerPhoneNumber
) {}