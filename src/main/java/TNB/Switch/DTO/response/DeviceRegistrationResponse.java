package TNB.Switch.DTO.response;


import java.util.UUID;

/**
 * Retournée UNIQUEMENT à l'enregistrement d'un device — porte le credential
 * en clair une seule fois (pattern "API key affichée une fois"). Le hash
 * seul est conservé en base ensuite ; ce credential ne pourra plus jamais
 * être récupéré, seulement régénéré.
 */
public record DeviceRegistrationResponse(
        UUID id,
        String pairingCode,
        String plainCredential
) {}