package TNB.Switch.controller;

import TNB.Switch.DTO.request.CreateCreditOfferRequest;
import TNB.Switch.DTO.request.CreateDataOfferRequest;
import TNB.Switch.DTO.request.CreateExchangeOfferRequest;
import TNB.Switch.DTO.response.OfferResponse;
import TNB.Switch.enums.OfferType;
import TNB.Switch.service.OfferService;
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
@RequestMapping("/offers")
@Tag(name = "Offres", description = "Gestion des offres de recharge crédit, data et échange Mobile Money")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    // =====================================================================
    //  ADMIN - CRÉATION
    // =====================================================================

    @Operation(
            summary = "Créer une offre CREDIT (Admin)",
            description = "Crée une offre de recharge crédit téléphonique. " +
                    "Le template de retrait et d'exécution sont automatiquement pris depuis l'opérateur source. " +
                    "L'offre est créée avec le statut 'active=true' par défaut."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre CREDIT créée avec succès",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (prix/montant négatif ou null)"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/credit")
    public ResponseEntity<OfferResponse> createCreditOffer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Informations de l'offre CREDIT",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "label": "MTN Good Deal 5000 FCFA",
                                              "price": 4500,
                                              "creditAmount": 5000,
                                              "offerFeePercentage": 5.0
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateCreditOfferRequest request) {
        return ResponseEntity.ok(offerService.createCreditOffer(request));
    }

    @Operation(
            summary = "Créer une offre DATA (Admin)",
            description = "Crée une offre de recharge data. " +
                    "Le template de retrait et d'exécution sont automatiquement pris depuis l'opérateur source."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre DATA créée avec succès",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (volume/validité négatifs)"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/data")
    public ResponseEntity<OfferResponse> createDataOffer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Informations de l'offre DATA",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "label": "Orange 10 Go - 30 jours",
                                              "price": 7500,
                                              "dataVolumeMb": 10240,
                                              "dataValidityDays": 30,
                                              "offerFeePercentage": 3.0
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateDataOfferRequest request) {
        return ResponseEntity.ok(offerService.createDataOffer(request));
    }

    @Operation(
            summary = "Créer une offre EXCHANGE_MO (Admin)",
            description = "Crée une offre d'échange Mobile Money entre deux opérateurs. " +
                    "Le template de retrait est automatiquement pris depuis l'opérateur source. " +
                    "Le template d'exécution correspond à la commande de DÉPÔT sur le wallet destination."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre EXCHANGE_MO créée avec succès",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (taux/frais négatifs, min > max)"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/exchange")
    public ResponseEntity<OfferResponse> createExchangeOffer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Informations de l'offre EXCHANGE_MO",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "label": "Orange → MTN MoMo",
                                              "exchangeRate": 1.0,
                                              "minAmount": 1000,
                                              "maxAmount": 100000,
                                              "offerFeePercentage": 2.5
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateExchangeOfferRequest request) {
        return ResponseEntity.ok(offerService.createExchangeOffer(request));
    }

    // =====================================================================
    //  ADMIN - ACTIVATION / DÉSACTIVATION
    // =====================================================================

    @Operation(
            summary = "Activer une offre (Admin)",
            description = "Rend une offre visible dans le catalogue client."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre activée",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offre non trouvée"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{offerId}/activate")
    public ResponseEntity<OfferResponse> activateOffer(
            @Parameter(description = "ID de l'offre à activer", required = true)
            @PathVariable UUID offerId) {
        return ResponseEntity.ok(offerService.activateOffer(offerId));
    }

    @Operation(
            summary = "Désactiver une offre (Admin)",
            description = "Masque une offre du catalogue client. Les transactions en cours ne sont pas affectées."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre désactivée",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offre non trouvée"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{offerId}/deactivate")
    public ResponseEntity<OfferResponse> deactivateOffer(
            @Parameter(description = "ID de l'offre à désactiver", required = true)
            @PathVariable UUID offerId) {
        return ResponseEntity.ok(offerService.deactivateOffer(offerId));
    }

    // =====================================================================
    //  LECTURE - CATALOGUE (Client / Admin)
    // =====================================================================

    @Operation(
            summary = "Liste toutes les offres actives (Client)",
            description = "Retourne le catalogue complet des offres disponibles à l'achat par les clients. " +
                    "Seules les offres avec 'active=true' sont retournées."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des offres actives",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class)))
    })
    @GetMapping("/active")
    public ResponseEntity<List<OfferResponse>> getActiveOffers() {
        return ResponseEntity.ok(offerService.getActiveOffers());
    }

    @Operation(
            summary = "Liste les offres actives par type (Client)",
            description = "Retourne le catalogue filtré par type d'offre : CREDIT, DATA ou EXCHANGE_MO."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des offres actives filtrées",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class)))
    })
    @GetMapping("/active/{type}")
    public ResponseEntity<List<OfferResponse>> getActiveOffersByType(
            @Parameter(description = "Type d'offre", required = true, schema = @Schema(allowableValues = {"CREDIT", "DATA", "EXCHANGE_MO"}))
            @PathVariable OfferType type) {
        return ResponseEntity.ok(offerService.getActiveOffersByType(type));
    }

    @Operation(
            summary = "Récupérer une offre par ID",
            description = "Retourne les détails complets d'une offre, qu'elle soit active ou non."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offre trouvée",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offre non trouvée")
    })
    @GetMapping("/{offerId}")
    public ResponseEntity<OfferResponse> getOffer(
            @Parameter(description = "ID de l'offre", required = true)
            @PathVariable UUID offerId) {
        return ResponseEntity.ok(offerService.getOffer(offerId));
    }

    @Operation(
            summary = "Liste toutes les offres (Admin)",
            description = "Retourne la liste complète de toutes les offres, actives ou non. " +
                    "Réservé aux administrateurs."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste complète des offres",
                    content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<OfferResponse>> getAllOffers() {
        return ResponseEntity.ok(offerService.getAllOffers());
    }
}