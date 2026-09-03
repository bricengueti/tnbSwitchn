package TNB.Switch.DTO.request;

import java.util.Map;
import java.util.UUID;

/**
 * Enregistrement d'un nouveau device.
 * operatorCommercialNumbers : opérateurId -> numéro commercial du device pour cet opérateur.
 */
public record RegisterDeviceRequest(
        String name,
        Map<UUID, String> operatorCommercialNumbers
) {
}