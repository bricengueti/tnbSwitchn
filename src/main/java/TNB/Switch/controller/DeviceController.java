package TNB.Switch.controller;

import TNB.Switch.DTO.request.RegisterDeviceRequest;
import TNB.Switch.DTO.response.DeviceRegistrationResponse;
import TNB.Switch.DTO.response.DeviceResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Device;
import TNB.Switch.service.DeviceService;
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
@RequestMapping("/devices")
@Tag(name = "Devices", description = "Gestion de la flotte de devices d'exécution (enregistrement, pause/reprise)")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // =====================================================================
    //  ENREGISTREMENT
    // =====================================================================

    @Operation(
            summary = "Enregistrer un nouveau device (Admin)",
            description = "Crée un device, l'associe aux opérateurs fournis avec leurs numéros commerciaux respectifs " +
                    "et initialise les soldes flotte correspondants. Le 'credential' retourné n'est affiché qu'une " +
                    "seule fois — il n'est plus récupérable ensuite (seul son hash est conservé)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device enregistré, pairingCode et credential générés",
                    content = @Content(schema = @Schema(implementation = DeviceRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Aucun opérateur fourni"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Un des opérateurs fournis n'existe pas")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DeviceRegistrationResponse> registerDevice(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Nom du device et numéros commerciaux par opérateur",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "name": "Device Douala-01",
                                              "operatorCommercialNumbers": {
                                                "550e8400-e29b-41d4-a716-446655440000": "677000001",
                                                "550e8400-e29b-41d4-a716-446655440001": "699000001"
                                              }
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody RegisterDeviceRequest request) {

        DeviceRegistrationResponse response = deviceService.registerDevice(
                request.name(), request.operatorCommercialNumbers()
        );

        return ResponseEntity.ok(response);
    }

    // =====================================================================
    //  PAUSE / REPRISE (Admin)
    // =====================================================================

    @Operation(
            summary = "Mettre un device en pause (Admin)",
            description = "Le device ne reçoit plus de nouvelles commandes tant qu'il n'est pas réactivé."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device mis en pause",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Device non trouvé"),
            @ApiResponse(responseCode = "409", description = "Transition de statut invalide depuis l'état actuel")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{deviceId}/pause")
    public ResponseEntity<DeviceResponse> pauseDevice(
            @Parameter(description = "ID du device", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID deviceId) {

        Device device = deviceService.pauseDevice(deviceId);
        return ResponseEntity.ok(toResponse(device));
    }

    @Operation(
            summary = "Réactiver un device en pause (Admin)",
            description = "Ramène le device en AVAILABLE. Échoue si le device n'était pas PAUSED."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device réactivé",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Device non trouvé"),
            @ApiResponse(responseCode = "409", description = "Le device n'est pas en pause")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{deviceId}/resume")
    public ResponseEntity<DeviceResponse> resumeDevice(
            @Parameter(description = "ID du device", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID deviceId) {

        Device device = deviceService.resumeDevice(deviceId);
        return ResponseEntity.ok(toResponse(device));
    }

    // =====================================================================
    //  LECTURE (Admin)
    // =====================================================================

    @Operation(
            summary = "Récupérer un device par ID (Admin)",
            description = "Retourne les informations publiques du device, y compris son pairingCode " +
                    "(jamais le credentialHash, qui n'est ni stocké en clair ni exposé)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device trouvé",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux administrateurs"),
            @ApiResponse(responseCode = "404", description = "Device non trouvé")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(
            @Parameter(description = "ID du device", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID deviceId) {

        Device device = deviceService.findById(deviceId);
        return ResponseEntity.ok(toResponse(device));
    }

    // =====================================================================
    //  MAPPING
    // =====================================================================

    /**
     * ASSOMPTION : Device#getId(), #getName(), #getPairingCode(), #getStatus(),
     * #getSupportedOperators() (retourne une collection d'Operateur), #getLastHeartbeat().
     * À ajuster si les noms réels diffèrent.
     */
    private DeviceResponse toResponse(Device device) {
        List<OperateurSummaryResponse> operators = device.getSupportedOperators().stream()
                .map(operateur -> new OperateurSummaryResponse(
                        operateur.getId(), operateur.getCode(), operateur.getNom()
                ))
                .toList();

        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getPairingCode(),
                device.getStatus(),
                operators,
                device.getLastHeartbeat()
        );
    }
}