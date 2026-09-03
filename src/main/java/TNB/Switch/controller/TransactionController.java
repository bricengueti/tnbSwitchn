package TNB.Switch.controller;

import TNB.Switch.DTO.request.ConfirmOtpRequest;
import TNB.Switch.DTO.request.CreateTransactionRequest;
import TNB.Switch.DTO.response.OfferSummaryResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.DTO.response.TransactionResponse;
import TNB.Switch.DTO.response.UserSummaryResponse;
import TNB.Switch.entity.Offer;
import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.Transaction;
import TNB.Switch.entity.User;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.OfferRepository;
import TNB.Switch.repository.OperateurRepository;
import TNB.Switch.repository.UserRepository;
import TNB.Switch.service.CustomUserDetails;
import TNB.Switch.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Cycle de vie des transactions (création, confirmation OTP, retrait, exécution)")
public class TransactionController {

    private final TransactionService transactionService;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final OperateurRepository operateurRepository;  // ✅ AJOUT

    public TransactionController(TransactionService transactionService,
                                 OfferRepository offerRepository,
                                 UserRepository userRepository,
                                 OperateurRepository operateurRepository) {
        this.transactionService = transactionService;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.operateurRepository = operateurRepository;
    }

    // =====================================================================
    //  CRÉATION - WAIT_OTP
    // =====================================================================

    @Operation(
            summary = "Créer une transaction (Client)",
            description = "Crée une transaction sur une offre CREDIT, DATA ou EXCHANGE_MO et déclenche l'envoi d'un OTP " +
                    "de confirmation vers le client authentifié. Idempotente via le header 'Idempotency-Key' : " +
                    "un rejeu avec la même clé retourne la transaction déjà créée sans renvoyer d'OTP. " +
                    "'destinationPhoneNumber' et 'payerPhoneNumber' sont obligatoires uniquement pour une offre EXCHANGE_MO. " +
                    "fromOperateurId et toOperateurId sont obligatoires pour toutes les transactions."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction créée (ou retrouvée si rejeu idempotent), statut WAIT_OTP",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (numéros manquants pour une offre EXCHANGE_MO)"),
            @ApiResponse(responseCode = "404", description = "Offre ou opérateur non trouvé")
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Parameter(description = "Clé d'idempotence générée par le client", required = true,
                    example = "3f2c9e1a-8b7d-4c6a-9e2f-1a2b3c4d5e6f")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Informations de la transaction",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "offerId": "550e8400-e29b-41d4-a716-446655440000",
                                              "fromOperateurId": "11111111-1111-1111-1111-111111111111",
                                              "toOperateurId": "22222222-2222-2222-2222-222222222222",
                                              "amount": 5000,
                                              "destinationPhoneNumber": "677123456",
                                              "payerPhoneNumber": "699987654"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        // 1. Récupérer l'offre
        Offer offer = offerRepository.findById(request.offerId())
                .orElseThrow(() -> new ResourceNotFoundException("Offer", request.offerId()));

        // 2. Récupérer l'utilisateur
        User client = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        // 3. ✅ Récupérer les opérateurs
        Operateur fromOperateur = operateurRepository.findById(request.fromOperateurId())
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", request.fromOperateurId()));

        Operateur toOperateur = operateurRepository.findById(request.toOperateurId())
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", request.toOperateurId()));

        // 4. Créer la transaction
        Transaction transaction = transactionService.createTransaction(
                idempotencyKey,
                client,
                offer,
                request.destinationPhoneNumber(),
                request.payerPhoneNumber(),
                fromOperateur,   // ✅ AJOUT
                toOperateur      // ✅ AJOUT
        );

        return ResponseEntity.ok(toResponse(transaction));
    }

    // =====================================================================
    //  CONFIRMATION OTP + RETRAIT - WAIT_OTP → QUEUE_WITHDRAWAL
    // =====================================================================

    @Operation(
            summary = "Confirmer l'OTP et déclencher le retrait (Client)",
            description = "Point d'entrée unique de confirmation : vérifie le code OTP puis met le retrait en file. " +
                    "Un seul geste atomique — jamais l'un sans l'autre."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP validé, retrait mis en file, statut QUEUE_WITHDRAWAL",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Code OTP invalide"),
            @ApiResponse(responseCode = "404", description = "Transaction non trouvée"),
            @ApiResponse(responseCode = "409", description = "Transaction plus en attente de confirmation OTP")
    })
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{transactionId}/confirm-otp")
    public ResponseEntity<TransactionResponse> confirmOtpAndQueueWithdrawal(
            @Parameter(description = "ID de la transaction", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID transactionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Code OTP reçu par le client",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "otpCode": "482913"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody ConfirmOtpRequest request) {

        Transaction transaction = transactionService.confirmOtpAndQueueWithdrawal(
                transactionId, request.otpCode()
        );

        return ResponseEntity.ok(toResponse(transaction));
    }

    // =====================================================================
    //  MAPPING
    // =====================================================================

    /**
     * TransactionResponse - Mapping entité → DTO.
     */
    private TransactionResponse toResponse(Transaction transaction) {
        Offer offer = transaction.getOffer();
        User client = transaction.getClient();
        Operateur fromOperateur = transaction.getFromOperateur();
        Operateur toOperateur = transaction.getToOperateur();

        return new TransactionResponse(
                transaction.getId(),
                UserSummaryResponse.fromEntity(client),
                OfferSummaryResponse.fromEntity(offer),
                OperateurSummaryResponse.fromEntity(fromOperateur),
                OperateurSummaryResponse.fromEntity(toOperateur),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getIdempotencyKey(),
                transaction.getDestinationPhoneNumber(),
                transaction.getPayerPhoneNumber(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}