package TNB.Switch.controller;


import TNB.Switch.DTO.request.ManualAdjustmentRequest;
import TNB.Switch.DTO.response.DeviceSummaryResponse;
import TNB.Switch.DTO.response.FleetBalanceResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.Operateur;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.repository.OperateurRepository;
import TNB.Switch.service.FleetBalanceService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fleet-balances")
@Tag(name = "Soldes Flotte", description = "Consultation et ajustement manuel des soldes flotte (crédit/wallet) par device/opérateur")
public class FleetBalanceController {

    private final FleetBalanceService fleetBalanceService;
    private final DeviceRepository deviceRepository;
    private final OperateurRepository operateurRepository;

    public FleetBalanceController(FleetBalanceService fleetBalanceService,
                                  DeviceRepository deviceRepository,
                                  OperateurRepository operateurRepository) {
        this.fleetBalanceService = fleetBalanceService;
        this.deviceRepository = deviceRepository;
        this.operateurRepository = operateurRepository;
    }

    // =====================================================================
    //  LECTURE (Admin)
    // =====================================================================

    @Operation(
            summary = "Consulter le solde flotte d'un device pour un opérateur (Admin)",
            description = "Lecture stricte : ne crée jamais implicitement un solde. Le couple (device, opérateur) " +
                    "doit avoir été enregistré au préalable via l'enregistrement du device."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Solde flotte trouvé",
                    content = @Content(schema = @Schema(implementation = FleetBalanceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Device, opérateur ou solde flotte non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<FleetBalanceResponse> getBalance(
            @Parameter(description = "ID du device", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam UUID deviceId,
            @Parameter(description = "ID de l'opérateur", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @RequestParam UUID operateurId) {

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));
        Operateur operateur = operateurRepository.findById(operateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", operateurId));

        FleetBalance balance = fleetBalanceService.getBalance(device, operateur);
        return ResponseEntity.ok(toResponse(balance));
    }

    @Operation(
            summary = "Lister les soldes flotte sous un seuil (Admin)",
            description = "Retourne les soldes (crédit ou wallet) passés sous le seuil fourni — pour alerte admin (CDC §7.7)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des soldes sous le seuil",
                    content = @Content(schema = @Schema(implementation = FleetBalanceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/below-threshold")
    public ResponseEntity<List<FleetBalanceResponse>> findBelowThreshold(
            @Parameter(description = "Seuil (FCFA)", required = true, example = "5000")
            @RequestParam BigDecimal threshold) {

        List<FleetBalanceResponse> balances = fleetBalanceService.findBelowThreshold(threshold).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(balances);
    }

    // =====================================================================
    //  AJUSTEMENT MANUEL (Admin)
    // =====================================================================

    @Operation(
            summary = "Ajustement manuel d'un solde flotte (Admin)",
            description = "Corrige le solde crédit d'un fleetBalance. 'signedAmount' positif crédite, négatif débite. " +
                    "La justification est obligatoire (CDC §7.7), contrairement aux mouvements automatiques."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Solde ajusté",
                    content = @Content(schema = @Schema(implementation = FleetBalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Justification manquante ou solde insuffisant pour un débit"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Solde flotte non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{fleetBalanceId}/manual-adjustment")
    public ResponseEntity<FleetBalanceResponse> manualAdjustment(
            @Parameter(description = "ID du solde flotte", required = true, example = "550e8400-e29b-41d4-a716-446655440002")
            @PathVariable UUID fleetBalanceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Montant signé et justification obligatoire",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "signedAmount": -15000,
                                              "justification": "Correction suite à erreur de saisie sur le device Douala-01"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody ManualAdjustmentRequest request) {

        FleetBalance balance = fleetBalanceService.manualAdjustment(
                fleetBalanceId, request.signedAmount(), request.justification()
        );

        return ResponseEntity.ok(toResponse(balance));
    }

    // =====================================================================
    //  MAPPING
    // =====================================================================

    /**
     * ASSOMPTION : FleetBalance#getId(), #getDevice(), #getOperateur(),
     * #getCommercialNumber(), #getCreditBalance(), #getWalletBalance().
     */
    private FleetBalanceResponse toResponse(FleetBalance balance) {
        Device device = balance.getDevice();
        Operateur operateur = balance.getOperateur();

        return new FleetBalanceResponse(
                balance.getId(),
                new DeviceSummaryResponse(device.getId(), device.getName(), device.getStatus()),
                new OperateurSummaryResponse(operateur.getId(), operateur.getCode(), operateur.getNom()),
                balance.getCommercialNumber(),
                balance.getCreditBalance(),
                balance.getWalletBalance()
        );
    }
}