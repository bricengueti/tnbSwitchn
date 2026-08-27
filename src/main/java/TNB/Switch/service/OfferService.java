package TNB.Switch.service;

import TNB.Switch.DTO.request.CreateCreditOfferRequest;
import TNB.Switch.DTO.request.CreateDataOfferRequest;
import TNB.Switch.DTO.request.CreateExchangeOfferRequest;
import TNB.Switch.DTO.response.OfferResponse;
import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Offer;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.OfferType;
import TNB.Switch.exeption.InvalidOfferConfigurationException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.mapper.OfferMapper;
import TNB.Switch.repository.OfferRepository;
import TNB.Switch.repository.OperateurRepository;
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
    private final OperateurRepository operateurRepository;
    private final OfferMapper offerMapper;

    public OfferService(OfferRepository offerRepository,
                        OperateurRepository operateurRepository,
                        OfferMapper offerMapper) {
        this.offerRepository = offerRepository;
        this.operateurRepository = operateurRepository;
        this.offerMapper = offerMapper;
    }

    /**
     * Crée une offre de recharge crédit ("good deal").
     * Le template de retrait est porté par l'opérateur.
     */
    @Transactional
    public OfferResponse createCreditOffer(CreateCreditOfferRequest request) {
        Operateur operateur = findOperateur(request.operateurId());

        requirePositive(request.price(), "prix");
        requirePositive(request.creditAmount(), "montant du crédit");
        validateOperateurHasWithdrawalTemplate(operateur);

        Offer offer = new Offer(OfferType.CREDIT, request.label(), operateur);
        offer.setPrice(request.price());
        offer.setCreditAmount(request.creditAmount());
        offer.setOfferFeePercentage(request.offerFeePercentage());
        offer.setExecutionCommandTemplate(new CommandTemplate(request.executionTemplateContent()));

        Offer saved = offerRepository.save(offer);
        log.info("Offre CREDIT créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    /**
     * Crée une offre de recharge data.
     * Le template de retrait est porté par l'opérateur.
     */
    @Transactional
    public OfferResponse createDataOffer(CreateDataOfferRequest request) {
        Operateur operateur = findOperateur(request.operateurId());

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
        validateOperateurHasWithdrawalTemplate(operateur);

        Offer offer = new Offer(OfferType.DATA, request.label(), operateur);
        offer.setPrice(request.price());
        offer.setDataVolumeMb(request.dataVolumeMb());
        offer.setDataValidityDays(request.dataValidityDays());
        offer.setOfferFeePercentage(request.offerFeePercentage());
        offer.setExecutionCommandTemplate(new CommandTemplate(request.executionTemplateContent()));

        Offer saved = offerRepository.save(offer);
        log.info("Offre DATA créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    /**
     * Crée une offre d'échange Mobile Money (switch de wallet).
     * Le template de retrait est porté par l'opérateur source.
     * Le template d'exécution = commande de DÉPÔT sur le wallet destination.
     */
    @Transactional
    public OfferResponse createExchangeOffer(CreateExchangeOfferRequest request) {
        Operateur sourceOperateur = findOperateur(request.sourceOperateurId());
        Operateur destinationOperateur = findOperateur(request.destinationOperateurId());

        if (sourceOperateur.getId().equals(destinationOperateur.getId())) {
            throw new InvalidOfferConfigurationException(
                    "L'opérateur de destination doit être différent de l'opérateur source"
            );
        }

        requirePositive(request.exchangeRate(), "taux d'échange");
        requirePositive(request.minAmount(), "montant minimum");
        requirePositive(request.maxAmount(), "montant maximum");

        if (request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new InvalidOfferConfigurationException(
                    "Le montant minimum ne peut pas dépasser le montant maximum"
            );
        }

        validateOperateurHasWithdrawalTemplate(sourceOperateur);

        Offer offer = new Offer(OfferType.EXCHANGE_MO, request.label(), sourceOperateur);
        offer.setDestinationOperator(destinationOperateur);
        offer.setPrice(BigDecimal.ZERO);
        offer.setExchangeRate(request.exchangeRate());
        offer.setMinAmount(request.minAmount());
        offer.setMaxAmount(request.maxAmount());
        offer.setOfferFeePercentage(request.offerFeePercentage());
        offer.setExecutionCommandTemplate(new CommandTemplate(request.executionTemplateContent()));

        Offer saved = offerRepository.save(offer);
        log.info("Offre EXCHANGE_MO créée [{}] : {}, frais {}%",
                saved.getId(), request.label(), request.offerFeePercentage());

        return offerMapper.apply(saved);
    }

    /**
     * Active une offre (visible côté client).
     */
    @Transactional
    public OfferResponse activateOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        offer.setActive(true);
        log.info("Offre [{}] activée", offerId);
        return offerMapper.apply(offer);
    }

    /**
     * Désactive une offre (masquée côté client).
     */
    @Transactional
    public OfferResponse deactivateOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        offer.setActive(false);
        log.info("Offre [{}] désactivée", offerId);
        return offerMapper.apply(offer);
    }

    /**
     * Récupère une offre par son ID.
     */
    public OfferResponse getOffer(UUID offerId) {
        Offer offer = findOffer(offerId);
        return offerMapper.apply(offer);
    }

    /**
     * Récupère toutes les offres actives (catalogue client).
     */
    public List<OfferResponse> getActiveOffers() {
        return offerRepository.findByActiveTrue().stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les offres actives par type (catalogue client filtré).
     */
    public List<OfferResponse> getActiveOffersByType(OfferType type) {
        return offerRepository.findByTypeAndActiveTrue(type).stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    /**
     * Récupère toutes les offres (admin).
     */
    public List<OfferResponse> getAllOffers() {
        return offerRepository.findAll().stream()
                .map(offerMapper)
                .collect(Collectors.toList());
    }

    // ==================== MÉTHODES PRIVÉES ====================

    /**
     * Vérifie que l'opérateur a bien un template de retrait configuré.
     * Sans cela, aucune commande de retrait ne peut être générée.
     */
    private void validateOperateurHasWithdrawalTemplate(Operateur operateur) {
        if (operateur.getWithdrawalCommandTemplate() == null) {
            throw new InvalidOfferConfigurationException(
                    "L'opérateur [%s] n'a pas de gabarit de retrait configuré. " +
                            "Veuillez configurer le template via OperateurService avant de créer une offre."
                                    .formatted(operateur.getCode())
            );
        }
    }

    private void requirePositive(BigDecimal value, String fieldLabel) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOfferConfigurationException(
                    "Le champ '%s' doit être strictement positif".formatted(fieldLabel)
            );
        }
    }

    private Operateur findOperateur(UUID operateurId) {
        return operateurRepository.findById(operateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", operateurId));
    }

    private Offer findOffer(UUID offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", offerId));
    }
}