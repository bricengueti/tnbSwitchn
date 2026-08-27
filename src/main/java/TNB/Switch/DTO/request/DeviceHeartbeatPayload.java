package TNB.Switch.DTO.request;


public record DeviceHeartbeatPayload(String status) {
    // "status" optionnel, informatif (ex. batterie faible côté device) —
    // le vrai statut métier (AVAILABLE/HOLDS/...) reste piloté côté
    // backend via DeviceService, jamais imposé par le device lui-même.
}