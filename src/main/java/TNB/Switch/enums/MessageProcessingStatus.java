package TNB.Switch.enums;


public enum MessageProcessingStatus {
    PENDING_AI,
    CLASSIFIED,
    PENDING_AI_RETRY,
    CLOSED_UNRELATED,
    AMBIGUOUS,
    RECONCILED
}