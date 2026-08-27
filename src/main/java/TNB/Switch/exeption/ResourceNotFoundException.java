package TNB.Switch.exeption;

import java.util.UUID;

/** Ressource demandée introuvable — HTTP 404. */
public class ResourceNotFoundException extends TnbException {

    public ResourceNotFoundException(String entityType, UUID id) {
        super("RESOURCE_NOT_FOUND",
                "%s introuvable pour l'identifiant [%s]".formatted(entityType, id));
    }

    public ResourceNotFoundException(String entityType, String identifier) {
        super("RESOURCE_NOT_FOUND",
                "%s introuvable pour l'identifiant [%s]".formatted(entityType, identifier));
    }
}