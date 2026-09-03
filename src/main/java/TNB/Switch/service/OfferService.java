package TNB.Switch.service;

import TNB.Switch.DTO.request.CreateCreditOfferRequest;
import TNB.Switch.DTO.request.CreateDataOfferRequest;
import TNB.Switch.DTO.request.CreateExchangeOfferRequest;
import TNB.Switch.DTO.response.OfferResponse;
import TNB.Switch.entity.Offer;
import TNB.Switch.enums.OfferType;
import TNB.Switch.exeption.InvalidOfferConfigurationException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.mapper.OfferMapper;
import TNB.Switch.repository.OfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offerRepository;
    private final OfferMapper offerMapper;

    public OfferService(OfferRepository offerRepository,
                        OfferMapper offerMapper) {
        this.offerRepository = offerRepository;
        this.offerMapper = offerMapper;
    }

    // ═══════════════════════════════════════════════════════
    //  CRÉATION DES OFFRES
    // ═══════════════════════════════════════════════════════

    @Transactional
    public OfferResponse createCreditOffer(CreateCreditOfferRequest request) {
        requirePositive(request.price(), "prix");
        requirePositive(request.creditAmount(), "montant du crédit");

        Offer offer = new Offer(OfferType.CREDIT, request.label());
        offer.setPrice(request.price());
        offer.setCreditAmount(request.creditAmount());
        offer.setOfferFeePercentage(request.offerFeePercentage());

        Offer saved = offerRepository.save(offer);
        log.info("Offre CREDIT créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    @Transactional
    public OfferResponse createDataOffer(CreateDataOfferRequest request) {
        requirePositive(request.price(), "prix");
        if (request.dataVolumeMb() <= 0) {
            throw new InvalidOfferConfigurationException(
                    "Une offre DATA doit définir un volume de données positif"
            );
        }
        if (request.dataValidityDays() <= 0) {
            throw new InvalidOfferConfigurationException(
                    "Une offre DATA doit définir une validité positive"
            );
        }

        Offer offer = new Offer(OfferType.DATA, request.label());
        offer.setPrice(request.price());
        offer.setDataVolumeMb(request.dataVolumeMb());
        offer.setDataValidityDays(request.dataValidityDays());
        offer.setOfferFeePercentage(request.offerFeePercentage());

        Offer saved = offerRepository.save(offer);
        log.info("Offre DATA créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    @Transactional
    public OfferResponse createExchangeOffer(CreateExchangeOfferRequest request) {
        requirePositive(request.exchangeRate(), "taux d'échange");
        requirePositive(request.minAmount(), "montant minimum");
        requirePositive(request.maxAmount(), "montant maximum");

        if (request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new InvalidOfferConfigurationException(
                    "Le montant minimum ne peut pas dépasser le montant maximum"
            );
        }

        Offer offer = new Offer(OfferType.EXCHANGE_MO, request.label());
        offer.setPrice(BigDecimal.ZERO);
        offer.setExchangeRate(request.exchangeRate());
        offer.setMinAmount(request.minAmount());
        offer.setMaxAmount(request.maxAmount());
        offer.setOfferFeePercentage(request.offerFeePercentage());

        Offer saved = offerRepository.save(offer);
        log.info("Offre EXCHANGE_MO créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    // ═══════════════════════════════════════════════════════
    //  ACTIVATION / DÉSACTIVATION
    // ═══════════════════════════════════════════════════════

    @Transactional
    public OfferResponse activateOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        offer.activer();
        log.info("Offre [{}] activée", offerId);
        return offerMapper.apply(offer);
    }

    @Transactional
    public OfferResponse deactivateOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        offer.desactiver();
        log.info("Offre [{}] désactivée", offerId);
        return offerMapper.apply(offer);
    }

    // ═══════════════════════════════════════════════════════
    //  LECTURE
    // ═══════════════════════════════════════════════════════

    public OfferResponse getOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        return offerMapper.apply(offer);
    }

    public Offer findOfferEntity(UUID offerId) {
        return findOffer(offerId);
    }

    public List<OfferResponse> getActiveOffers() {
        return offerRepository.findByActiveTrue().stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    public List<OfferResponse> getActiveOffersByType(OfferType type) {
        return offerRepository.findByTypeAndActiveTrue(type).stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    public List<OfferResponse> getAllOffers() {
        return offerRepository.findAll().stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ═══════════════════════════════════════════════════════

    private void requirePositive(BigDecimal value, String fieldLabel) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOfferConfigurationException(
                    "Le champ '%s' doit être strictement positif".formatted(fieldLabel)
            );
        }
    }

    private Offer findOffer(UUID offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", offerId));
    }
}