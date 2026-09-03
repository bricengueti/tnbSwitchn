package TNB.Switch.seeder;

import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.PhonePrefix;
import TNB.Switch.enums.OperateurType;
import TNB.Switch.enums.OfferType;
import TNB.Switch.repository.OperateurRepository;
import TNB.Switch.repository.PhonePrefixRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class OperateurSeeder implements CommandLineRunner {

    private final OperateurRepository operateurRepository;
    private final PhonePrefixRepository phonePrefixRepository;

    // Indicatif international du Cameroun
    private static final String COUNTRY_CODE = "+237";
    private static final String COUNTRY_CODE_WITHOUT_PLUS = "237";

    public OperateurSeeder(OperateurRepository operateurRepository,
                           PhonePrefixRepository phonePrefixRepository) {
        this.operateurRepository = operateurRepository;
        this.phonePrefixRepository = phonePrefixRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedMTN();
        seedOrange();
        seedCamtel();
        seedYoomee();
    }

    // =====================================================================
    //  MTN CAMEROUN
    //  Préfixes : 650-654, 670-679, 680-683, 689
    // =====================================================================

    private void seedMTN() {
        Operateur operateur = createOrGetOperateur(
                "MTN",
                "MTN Cameroun",
                OperateurType.TELECOM_ET_MOBILE_MONEY,
                new CommandTemplate("*126*14*{commercialNumber}*{amount}#"),
                Map.of(
                        OfferType.CREDIT, new CommandTemplate("*126*14*{destinationPhoneNumber}*{creditAmount}#"),
                        OfferType.DATA, new CommandTemplate("*126*17*{destinationPhoneNumber}*{dataVolumeMb}#"),
                        OfferType.EXCHANGE_MO, new CommandTemplate("*126*18*{destinationPhoneNumber}*{amount}#")
                )
        );

        if (operateur != null) {
            seedPrefixes(operateur,
                    "650", "651", "652", "653", "654",
                    "670", "671", "672", "673", "674", "675", "676", "677", "678", "679",
                    "680", "681", "682", "683",
                    "689"
            );
        }
    }

    // =====================================================================
    //  ORANGE CAMEROUN
    //  Préfixes : 655-659, 690-699, 686-688
    // =====================================================================

    private void seedOrange() {
        Operateur operateur = createOrGetOperateur(
                "ORANGE",
                "Orange Cameroun",
                OperateurType.TELECOM_ET_MOBILE_MONEY,
                new CommandTemplate("*144*{commercialNumber}*{amount}#"),
                Map.of(
                        OfferType.CREDIT, new CommandTemplate("*144*{destinationPhoneNumber}*{creditAmount}#"),
                        OfferType.DATA, new CommandTemplate("*144*{destinationPhoneNumber}*{dataVolumeMb}#"),
                        OfferType.EXCHANGE_MO, new CommandTemplate("*144*{destinationPhoneNumber}*{amount}#")
                )
        );

        if (operateur != null) {
            seedPrefixes(operateur,
                    "655", "656", "657", "658", "659",
                    "686", "687", "688",
                    "690", "691", "692", "693", "694", "695", "696", "697", "698", "699"
            );
        }
    }

    // =====================================================================
    //  CAMTEL
    // =====================================================================

    private void seedCamtel() {
        Operateur operateur = createOrGetOperateur(
                "CAMTEL",
                "Camtel",
                OperateurType.TELECOM,
                new CommandTemplate("TODO_CAMTEL_WITHDRAWAL_USSD"),
                Map.of(
                        OfferType.CREDIT, new CommandTemplate("TODO_CAMTEL_CREDIT_USSD"),
                        OfferType.DATA, new CommandTemplate("TODO_CAMTEL_DATA_USSD"),
                        OfferType.EXCHANGE_MO, new CommandTemplate("TODO_CAMTEL_EXCHANGE_USSD")
                )
        );

        if (operateur != null) {
            seedPrefixes(operateur,
                    "600", "601", "602", "603", "604", "605", "606", "607", "608", "609",
                    "610", "611", "612", "613", "614", "615", "616", "617", "618", "619"
            );
        }
    }

    // =====================================================================
    //  YOOMEE
    // =====================================================================

    private void seedYoomee() {
        Operateur operateur = createOrGetOperateur(
                "YOOMEE",
                "Yoomee",
                OperateurType.TELECOM,
                new CommandTemplate("TODO_YOOMEE_WITHDRAWAL_USSD"),
                Map.of(
                        OfferType.CREDIT, new CommandTemplate("TODO_YOOMEE_CREDIT_USSD"),
                        OfferType.DATA, new CommandTemplate("TODO_YOOMEE_DATA_USSD"),
                        OfferType.EXCHANGE_MO, new CommandTemplate("TODO_YOOMEE_EXCHANGE_USSD")
                )
        );

        if (operateur != null) {
            seedPrefixes(operateur,
                    "670", "671", "672", "673", "674", "675"
            );
        }
    }

    // =====================================================================
    //  MÉTHODES UTILITAIRES
    // =====================================================================

    private Operateur createOrGetOperateur(String code, String nom, OperateurType type,
                                           CommandTemplate withdrawalTemplate,
                                           Map<OfferType, CommandTemplate> executionTemplates) {
        return operateurRepository.findByCode(code)
                .map(existing -> {
                    System.out.println("⏭️ Opérateur déjà existant : " + code);
                    return existing;
                })
                .orElseGet(() -> {
                    Operateur operateur = new Operateur(code, nom, type, withdrawalTemplate);
                    if (executionTemplates != null) {
                        executionTemplates.forEach(operateur::addExecutionTemplate);
                    }
                    operateur = operateurRepository.save(operateur);
                    System.out.println("✅ Opérateur créé : " + code);
                    return operateur;
                });
    }

    private void seedPrefixes(Operateur operateur, String... prefixes) {
        for (String prefix : prefixes) {
            seedPrefix(operateur, prefix);
        }
    }

    private void seedPrefix(Operateur operateur, String prefix) {
        if (phonePrefixRepository.findByPrefix(prefix).isEmpty()) {
            phonePrefixRepository.save(new PhonePrefix(prefix, operateur));
            System.out.println("  ✅ Préfixe ajouté : " + prefix + " → " + operateur.getCode());
        }
    }

    // =====================================================================
    //  MÉTHODES DE NORMALISATION POUR PhoneNumberValidationService
    // =====================================================================

    /**
     * Normalise un numéro de téléphone pour la validation.
     * Gère les formats : +237650123456, 237650123456, 650123456
     */
    public static String normalizeForValidation(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        // Enlever les espaces, tirets, points
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        // Si le numéro commence par +237, enlever +237
        if (cleaned.startsWith("+237")) {
            cleaned = cleaned.substring(4);
        }
        // Si le numéro commence par 237, enlever 237
        else if (cleaned.startsWith("237")) {
            cleaned = cleaned.substring(3);
        }

        // Si le numéro commence par 0, enlever le 0
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }

        return cleaned;
    }

    /**
     * Vérifie si un numéro appartient à un opérateur.
     */
    public static boolean isValidForOperateur(String phoneNumber, String operateurPrefix) {
        String normalized = normalizeForValidation(phoneNumber);
        return normalized != null && normalized.startsWith(operateurPrefix);
    }
}