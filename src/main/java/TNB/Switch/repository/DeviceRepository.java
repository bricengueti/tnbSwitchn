package TNB.Switch.repository;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByPairingCode(String pairingCode);

    // Utilisé par le RoutingService : liste des devices AVAILABLE supportant
    // un opérateur donné, triés par dernier heartbeat (le plus récemment
    // actif d'abord — heuristique simple de fiabilité de connexion).
    @Query("""
        SELECT d FROM Device d
        JOIN d.supportedOperators op
        WHERE d.status = :status AND op = :operateur
        ORDER BY d.lastHeartbeat DESC
        """)
    List<Device> findAvailableByOperateur(
            @Param("status") DeviceStatus status,
            @Param("operateur") Operateur operateur
    );

    // Verrouillage pessimiste explicite sur un device précis, pour
    // sécuriser la transition AVAILABLE -> HOLDS au moment de l'affectation
    // (protège la race condition identifiée en review d'architecture :
    // deux threads ne doivent jamais matcher le même device simultanément).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Device d WHERE d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") UUID id);

    // Supervision admin : devices en heartbeat expiré mais pas encore
    // marqués OFFLINE — signal pour le job de détection de perte de connexion.
    @Query("""
    SELECT d FROM Device d
    WHERE d.status <> :onlineStatus
    AND (d.lastHeartbeat IS NULL OR d.lastHeartbeat < :threshold)
    """)
    List<Device> findStaleHeartbeats(
            @Param("onlineStatus") DeviceStatus onlineStatus,
            @Param("threshold") java.time.Instant threshold
    );
}