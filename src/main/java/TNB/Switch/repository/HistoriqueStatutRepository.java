package TNB.Switch.repository;
import TNB.Switch.entity.HistoriqueStatut;
import TNB.Switch.enums.AuditedEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, UUID> {

    // Reconstruction du fil d'audit complet d'une entité donnée
    // (transaction, device, ou message opérateur), dans l'ordre chronologique.
    List<HistoriqueStatut> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
            AuditedEntityType entityType, UUID entityId
    );
}