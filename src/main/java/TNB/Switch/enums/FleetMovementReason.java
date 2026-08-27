package TNB.Switch.enums;


public enum FleetMovementReason {
    MANUAL_TOPUP,        // approvisionnement manuel par l'admin
    MANUAL_CORRECTION,    // correction manuelle (écart constaté, litige)
    TRANSACTION_DEBIT,     // débit automatique lors de l'exécution d'une commande
    TRANSACTION_CREDIT     // recrédit automatique (ex. compensation après échec)
}