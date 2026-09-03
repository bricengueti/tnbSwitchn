package TNB.Switch.controller;
import TNB.Switch.DTO.request.ResolveManuallyRequest;
import TNB.Switch.DTO.response.PendingCompensationResponse;
import TNB.Switch.DTO.response.PendingReconciliationResponse;
import TNB.Switch.DTO.response.StuckCommandeResponse;
import TNB.Switch.service.AdminSupervisionService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/supervision")
@Tag(name = "Supervision Admin", description = "Les 3 files de reprise manuelle : réconciliation ambiguë, compensation bloquée, commandes jamais routées")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupervisionController {

    private final AdminSupervisionService adminSupervisionService;

    public AdminSupervisionController(AdminSupervisionService adminSupervisionService) {
        this.adminSupervisionService = adminSupervisionService;
    }

    // =====================================================================
    //  FILE 1 : RÉCONCILIATION (messages AMBIGUOUS)
    // =====================================================================

    @Operation(
            summary = "Lister les messages opérateurs en attente de reprise manuelle (Admin)",
            description = "Messages en statut AMBIGUOUS n'ayant pas pu être rattachés automatiquement à une commande (§9.3bis)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des messages en attente",
                    content = @Content(schema = @Schema(implementation = PendingReconciliationResponse.class)))
    })
    @GetMapping("/reconciliation")
    public ResponseEntity<List<PendingReconciliationResponse>> findPendingReconciliation() {
        return ResponseEntity.ok(adminSupervisionService.findPendingReconciliation());
    }

    @Operation(
            summary = "Résoudre manuellement un message ambigu (Admin)",
            description = "L'admin rattache lui-même le message à la Commande concernée et indique si le résultat " +
                    "est un succès ou un échec. Déclenche la suite normale de la state machine Transaction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message résolu et transaction mise à jour"),
            @ApiResponse(responseCode = "404", description = "Message ou commande non trouvé(e)")
    })
    @PatchMapping("/reconciliation/{messageId}/resolve")
    public ResponseEntity<Void> resolveManually(
            @Parameter(description = "ID du message opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID messageId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Commande rattachée et résultat",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "commandeId": "550e8400-e29b-41d4-a716-446655440001",
                                              "success": true
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody ResolveManuallyRequest request) {

        adminSupervisionService.resolveManually(messageId, request.commandeId(), request.success());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Rejeter un message comme hors-sujet (Admin)",
            description = "Marque le message CLOSED_UNRELATED : il ne correspond à aucune commande du système."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message rejeté"),
            @ApiResponse(responseCode = "404", description = "Message non trouvé")
    })
    @PatchMapping("/reconciliation/{messageId}/dismiss")
    public ResponseEntity<Void> dismissAsUnrelated(
            @Parameter(description = "ID du message opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID messageId) {

        adminSupervisionService.dismissAsUnrelated(messageId);
        return ResponseEntity.ok().build();
    }

    // =====================================================================
    //  FILE 2 : COMPENSATION BLOQUÉE (COMPENSATION_MANUAL_REVIEW)
    // =====================================================================

    @Operation(
            summary = "Lister les transactions en compensation bloquée (Admin)",
            description = "Transactions en COMPENSATION_MANUAL_REVIEW : retrait réussi, exécution échouée, " +
                    "3 tentatives de compensation automatique épuisées (§8.4)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des transactions en attente",
                    content = @Content(schema = @Schema(implementation = PendingCompensationResponse.class)))
    })
    @GetMapping("/compensation-review")
    public ResponseEntity<List<PendingCompensationResponse>> findPendingCompensationReview() {
        return ResponseEntity.ok(adminSupervisionService.findPendingCompensationReview());
    }

    @Operation(
            summary = "Relancer manuellement une compensation bloquée (Admin)",
            description = "Retente la compensation malgré l'épuisement des 3 tentatives automatiques. " +
                    "Uniquement possible depuis le statut COMPENSATION_MANUAL_REVIEW."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relance de compensation publiée"),
            @ApiResponse(responseCode = "404", description = "Transaction non trouvée"),
            @ApiResponse(responseCode = "409", description = "Transaction pas en COMPENSATION_MANUAL_REVIEW ou aucune commande EXECUTION trouvée")
    })
    @PatchMapping("/compensation-review/{transactionId}/retry")
    public ResponseEntity<Void> retryCompensationManually(
            @Parameter(description = "ID de la transaction", required = true, example = "550e8400-e29b-41d4-a716-446655440002")
            @PathVariable UUID transactionId) {

        adminSupervisionService.retryCompensationManually(transactionId);
        return ResponseEntity.ok().build();
    }

    // =====================================================================
    //  FILE 3 : COMMANDES JAMAIS ROUTÉES (DLQ)
    // =====================================================================

    @Operation(
            summary = "Lister les commandes jamais routées (Admin)",
            description = "Commandes sans device affecté depuis plus de 'thresholdSeconds' — file d'attente de routage bloquée."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des commandes bloquées",
                    content = @Content(schema = @Schema(implementation = StuckCommandeResponse.class)))
    })
    @GetMapping("/stuck-commands")
    public ResponseEntity<List<StuckCommandeResponse>> findStuckUnroutedCommands(
            @Parameter(description = "Seuil en secondes au-delà duquel une commande sans device est considérée bloquée",
                    required = true, example = "300")
            @RequestParam int thresholdSeconds) {

        return ResponseEntity.ok(adminSupervisionService.findStuckUnroutedCommands(thresholdSeconds));
    }

    @Operation(
            summary = "Forcer le reroutage d'une commande bloquée (Admin)",
            description = "Republie manuellement la commande sur le topic de routage. Échoue si elle est déjà routée."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Commande republiée pour routage"),
            @ApiResponse(responseCode = "404", description = "Commande non trouvée"),
            @ApiResponse(responseCode = "409", description = "Commande déjà routée vers un device")
    })
    @PatchMapping("/stuck-commands/{commandeId}/force-reroute")
    public ResponseEntity<Void> forceReroute(
            @Parameter(description = "ID de la commande", required = true, example = "550e8400-e29b-41d4-a716-446655440003")
            @PathVariable UUID commandeId) {

        adminSupervisionService.forceReroute(commandeId);
        return ResponseEntity.ok().build();
    }
}