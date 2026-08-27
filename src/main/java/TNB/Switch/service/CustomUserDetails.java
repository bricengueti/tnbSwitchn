package TNB.Switch.service;

import TNB.Switch.entity.User;
import TNB.Switch.enums.UserAccountStatus;
import TNB.Switch.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapte User aux mécanismes standard de Spring Security. isAdmin est
 * dérivé de UserRole (cf. décision session : pas d'entité Admin séparée),
 * exposé à la fois via getAuthorities() (pour @PreAuthorize/hasRole) et
 * via isAdmin() (raccourci direct pour la réponse d'authentification).
 */
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String phoneNumber;
    private final UserRole role;
    private final UserAccountStatus accountStatus;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.phoneNumber = user.getPhoneNumber();
        this.role = user.getRole();
        this.accountStatus = user.getAccountStatus();
    }

    public UUID getId() { return id; }

    public boolean isAdmin() { return role == UserRole.ADMIN; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        // Pas de mot de passe — authentification OTP uniquement
        // (cf. décision session : Admin fusionné dans User, pas de credential).
        return null;
    }

    @Override
    public String getUsername() { return phoneNumber; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        // Un compte SUSPENDED ne doit plus pouvoir s'authentifier du tout.
        return accountStatus != UserAccountStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return accountStatus == UserAccountStatus.ACTIVE;
    }
}