package TNB.Switch.seeder;

import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.OperateurType;
import TNB.Switch.repository.OperateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class OperateurSeeder implements CommandLineRunner {

    private final OperateurRepository repository;

    public OperateurSeeder(OperateurRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        seedIfAbsent("MTN", "MTN Cameroun", OperateurType.TELECOM_ET_MOBILE_MONEY,
                new CommandTemplate("*126*14*{commercialNumber}*{amount}#"));
        seedIfAbsent("ORANGE", "Orange Cameroun", OperateurType.TELECOM_ET_MOBILE_MONEY,
                new CommandTemplate("TODO_ORANGE_WITHDRAWAL_USSD"));
        seedIfAbsent("CAMTEL", "Camtel", OperateurType.TELECOM,
                new CommandTemplate("TODO_CAMTEL_WITHDRAWAL_USSD"));
        seedIfAbsent("YOOMEE", "Yoomee", OperateurType.TELECOM,
                new CommandTemplate("TODO_YOOMEE_WITHDRAWAL_USSD"));
    }

    private void seedIfAbsent(String code, String nom, OperateurType type, CommandTemplate withdrawalCommandTemplate) {
        if (repository.findByCode(code).isEmpty()) {
            repository.save(new Operateur(code, nom, type, withdrawalCommandTemplate));
        }
    }
}