package TNB.Switch.enums;
public enum TransactionStatus {
    WAIT_OTP,
    QUEUE_WITHDRAWAL,
    ASK_WITHDRAWAL,
    WITHDRAWAL_DONE,
    WITHDRAWAL_FAILED,
    QUEUE_EXECUTE_COMMAND,
    ROUTE_EXECUTE_COMMAND,
    EXECUTE_COMMAND_DONE,
    EXECUTE_COMMAND_FAILED,
    CANCELLED,
    // Ajout à TransactionStatus
    COMPENSATION_IN_PROGRESS,   // retry automatique en cours (jusqu'à 3 tentatives)
    COMPENSATION_MANUAL_REVIEW  // 3 tentatives épuisées, attente admin — état terminal automatique
}