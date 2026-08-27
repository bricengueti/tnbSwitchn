package TNB.Switch.repository;

import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.Operateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetBalanceRepository extends JpaRepository<FleetBalance, UUID> {

    Optional<FleetBalance> findByDeviceAndOperateur(Device device, Operateur operateur);

    // Verrouillage pessimiste explicite : utilisé par FleetBalanceService
    // au moment du débit/crédit pour empêcher deux écritures concurrentes
    // de se baser sur une valeur de solde périmée (en plus du @Version
    // hérité, qui protège au niveau optimiste en cas de conflit détecté
    // après coup — le lock pessimiste évite le conflit en amont).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT fb FROM FleetBalance fb
        WHERE fb.device = :device AND fb.operateur = :operateur
        """)
    Optional<FleetBalance> findByDeviceAndOperateurForUpdate(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur
    );

    // Vue admin : soldes bas nécessitant un réapprovisionnement (§7.7).
    @Query("""
        SELECT fb FROM FleetBalance fb
        WHERE fb.creditBalance < :threshold OR fb.walletBalance < :threshold
        """)
    List<FleetBalance> findBelowThreshold(@Param("threshold") java.math.BigDecimal threshold);
    // Ajout à FleetBalanceRepository

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fb FROM FleetBalance fb WHERE fb.id = :id")
    Optional<FleetBalance> findByIdForUpdate(@Param("id") UUID id);
}