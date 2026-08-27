package TNB.Switch.repository;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.FleetMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FleetMovementRepository extends JpaRepository<FleetMovement, UUID> {

    // Historique des mouvements pour un solde donné (§7.7 : traçabilité obligatoire).
    List<FleetMovement> findByFleetBalanceOrderByCreatedAtDesc(FleetBalance fleetBalance);

    List<FleetMovement> findByTransactionId(UUID transactionId);
}