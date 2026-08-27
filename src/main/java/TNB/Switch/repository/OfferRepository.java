package TNB.Switch.repository;

import TNB.Switch.entity.Offer;
import TNB.Switch.enums.OfferType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    // Catalogue affiché côté client — uniquement les offres actives.
    List<Offer> findByActiveTrue();

    List<Offer> findByTypeAndActiveTrue(OfferType type);
}