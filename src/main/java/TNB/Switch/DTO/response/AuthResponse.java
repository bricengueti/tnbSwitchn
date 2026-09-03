package TNB.Switch.DTO.response;

import TNB.Switch.config.TokenPair;

/**
 * Réponse de connexion — isAdmin permet au frontend d'orienter vers le bon
 * parcours (cf. décision session : admin = User avec role=ADMIN).
 */
public record AuthResponse(TokenPair tokenPair, boolean isAdmin) {}