package TNB.Switch.controller;

import TNB.Switch.DTO.request.CreateOperateurRequest;
import TNB.Switch.DTO.request.UpdateOperateurRequest;
import TNB.Switch.DTO.request.UpdateWithdrawalTemplateRequest;
import TNB.Switch.DTO.response.OperateurResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.service.OperateurService;
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
@RequestMapping("/operateurs")
@Tag(name = "Opérateurs", description = "Gestion des opérateurs (MTN, Orange, etc.) et de leurs gabarits de commande")
public class OperateurController {

    private final OperateurService operateurService;

    public OperateurController(OperateurService operateurService) {
        this.operateurService = operateurService;
    }

    // =====================================================================
    //  LECTURE
    // =====================================================================

    @Operation(
            summary = "Lister les opérateurs actifs",
            description = "Catalogue des opérateurs actifs — consultable par tout utilisateur authentifié " +
                    "(nécessaire pour construire une transaction EXCHANGE_MO par exemple)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des opérateurs actifs",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class)))
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/active")
    public ResponseEntity<List<OperateurResponse>> getActiveOperateurs() {
        return ResponseEntity.ok(operateurService.getActiveOperateurs());
    }

    @Operation(
            summary = "Récupérer le résumé d'un opérateur par ID",
            description = "Version allégée (id, code, nom) — pratique pour peupler des listes déroulantes côté client."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Résumé de l'opérateur",
                    content = @Content(schema = @Schema(implementation = OperateurSummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Opérateur non trouvé")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/summary")
    public ResponseEntity<OperateurSummaryResponse> getOperateurSummary(
            @Parameter(description = "ID de l'opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {

        return ResponseEntity.ok(operateurService.getOperateurSummary(id));
    }

    // =====================================================================
    //  CRÉATION / MODIFICATION (Admin)
    // =====================================================================

    @Operation(
            summary = "Créer un opérateur (Admin)",
            description = "Le gabarit de retrait (withdrawalTemplateContent) est obligatoire à la création (CDC §7.2). " +
                    "Le code doit être unique."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opérateur créé",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class))),
            @ApiResponse(responseCode = "400", description = "Code déjà utilisé, ou gabarit de retrait manquant"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<OperateurResponse> createOperateur(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Informations de l'opérateur",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MTN",
                                              "nom": "MTN Mobile Money",
                                              "type": "MOBILE_MONEY",
                                              "withdrawalTemplateContent": "*126*1*{amount}*{pin}#"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateOperateurRequest request) {

        return ResponseEntity.ok(operateurService.createOperateur(request));
    }

    @Operation(
            summary = "Mettre à jour un opérateur (Admin)",
            description = "Mise à jour partielle : seuls les champs non nuls/non vides du body sont appliqués. " +
                    "L'id de l'opérateur à modifier doit être fourni dans le body."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opérateur mis à jour",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Opérateur non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping
    public ResponseEntity<OperateurResponse> updateOperateur(
            @Valid @RequestBody UpdateOperateurRequest request) {

        return ResponseEntity.ok(operateurService.updateOperateur(request));
    }

    @Operation(
            summary = "Mettre à jour le gabarit de retrait d'un opérateur (Admin)",
            description = "Endpoint dédié, séparé de la mise à jour générale, car le gabarit de retrait est une " +
                    "donnée sensible (impacte directement l'exécution des retraits en production)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Gabarit mis à jour",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class))),
            @ApiResponse(responseCode = "400", description = "Contenu du gabarit vide"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Opérateur non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/withdrawal-template")
    public ResponseEntity<OperateurResponse> updateWithdrawalTemplate(
            @Valid @RequestBody UpdateWithdrawalTemplateRequest request) {

        return ResponseEntity.ok(operateurService.updateWithdrawalTemplate(request));
    }

    // =====================================================================
    //  ACTIVATION / DÉSACTIVATION (Admin)
    // =====================================================================

    @Operation(summary = "Activer un opérateur (Admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opérateur activé",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Opérateur non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<OperateurResponse> activate(
            @Parameter(description = "ID de l'opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {

        return ResponseEntity.ok(operateurService.activate(id));
    }

    @Operation(summary = "Désactiver un opérateur (Admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opérateur désactivé",
                    content = @Content(schema = @Schema(implementation = OperateurResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Opérateur non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<OperateurResponse> deactivate(
            @Parameter(description = "ID de l'opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {

        return ResponseEntity.ok(operateurService.deactivate(id));
    }
}