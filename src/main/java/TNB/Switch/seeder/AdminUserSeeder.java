package TNB.Switch.seeder;

import TNB.Switch.entity.User;
import TNB.Switch.enums.UserAccountStatus;
import TNB.Switch.enums.UserRole;
import TNB.Switch.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Crée le premier compte admin au démarrage — un simple User avec
 * role=ADMIN, pas d'entité ni de mécanisme d'authentification séparés.
 * L'admin se connectera ensuite exactement comme un utilisateur normal
 * (numéro de téléphone + OTP). Désactivé par défaut, jamais actif en
 * profil test.
 */
@Component
@Profile("!test")
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final boolean enabled;
    private final String phoneNumber;

    public AdminUserSeeder(
            UserRepository userRepository,
            @Value("${tnb.seed.admin.enabled:false}") boolean enabled,
            @Value("${tnb.seed.admin.phone-number:}") String phoneNumber) {
        this.userRepository = userRepository;
        this.enabled = enabled;
        this.phoneNumber = phoneNumber;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        if (phoneNumber.isBlank()) {
            throw new IllegalStateException(
                    "Seed admin activé (tnb.seed.admin.enabled=true) mais "
                            + "tnb.seed.admin.phone-number est manquant — démarrage "
                            + "interrompu pour éviter un admin mal configuré."
            );
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            return; // idempotent
        }

        User admin = new User(phoneNumber);
        admin.setRole(UserRole.ADMIN);
        // Compte pré-activé : le premier admin doit pouvoir se connecter
        // immédiatement sans passer par le flux de vérification standard.
        admin.setAccountStatus(UserAccountStatus.ACTIVE);
        userRepository.save(admin);
    }
}