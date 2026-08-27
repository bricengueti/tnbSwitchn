package TNB.Switch.entity;

import TNB.Switch.enums.UserAccountStatus;
import TNB.Switch.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_phone_number", columnNames = "phone_number")
)
public class User extends BaseAuditableEntity {

    @Column(name = "phone_number", nullable = false, updatable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private UserAccountStatus accountStatus = UserAccountStatus.PENDING_VERIFICATION;

    // Rôle par défaut USER — seul le seeder ou une action admin explicite
    // peut positionner ADMIN.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role = UserRole.USER;

    protected User() {
        // requis par JPA
    }

    public User(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumber() { return phoneNumber; }
    public UserAccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(UserAccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    // Utilitaire de lecture simple, pas de logique métier — juste un raccourci
    // pratique pour le service qui construit la réponse d'authentification.
    public boolean isAdmin() { return role == UserRole.ADMIN; }
}