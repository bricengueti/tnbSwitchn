package TNB.Switch.repository;

import TNB.Switch.entity.Otp;
import TNB.Switch.entity.User;
import TNB.Switch.enums.OtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<Otp, UUID> {

    // Le dernier OTP émis pour un utilisateur = celui à valider en priorité.
    List<Otp> findByUserAndStatusOrderByCreatedAtDesc(User user, OtpStatus status);

    // Anti brute-force (§7.1) : compte les OTP émis récemment pour throttling.
    long countByUserAndCreatedAtAfter(User user, java.time.Instant since);
    // Ajout à OtpRepository

    @Query("""
    SELECT o FROM Otp o
    WHERE o.status = :status AND o.expiresAt < :now
    """)
    List<Otp> findExpiredPending(@Param("status") OtpStatus status, @Param("now") Instant now);
}