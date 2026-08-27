package TNB.Switch.enums;

public enum OperateurType {
    TELECOM,              // recharge crédit/data uniquement (ex. Camtel si pas de MO)
    MOBILE_MONEY,          // wallet uniquement
    TELECOM_ET_MOBILE_MONEY // les deux (ex. MTN, Orange)
}