package TNB.Switch.service;

import TNB.Switch.entity.User;
import TNB.Switch.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomUserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Chargement par UUID (extrait du token JWT) — pas par phoneNumber,
     * puisque c'est l'identifiant porté par le subject du token.
     */
    public CustomUserDetails loadUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur introuvable pour l'identifiant [%s]".formatted(userId)
                ));
        return new CustomUserDetails(user);
    }
}