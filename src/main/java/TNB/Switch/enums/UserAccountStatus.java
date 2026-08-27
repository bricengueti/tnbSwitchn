package TNB.Switch.enums;


public enum UserAccountStatus {
    PENDING_VERIFICATION,  // inscrit mais OTP jamais validé
    ACTIVE,
    SUSPENDED               // action admin (fraude suspectée, litige...)
}