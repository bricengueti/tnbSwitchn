package TNB.Switch.DTO.response;

/**
 * Réponse de connexion — isAdmin permet au frontend d'orienter vers le bon
 * parcours (cf. décision session : admin = User avec role=ADMIN).
 */
public record AuthResponse(String accessToken, boolean isAdmin) {}