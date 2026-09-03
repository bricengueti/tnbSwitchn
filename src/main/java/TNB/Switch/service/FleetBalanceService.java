package TNB.Switch.service;

import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.FleetMovement;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.FleetMovementReason;
import TNB.Switch.exeption.InsufficientFleetBalanceException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.FleetBalanceRepository;
import TNB.Switch.repository.FleetMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FleetBalanceService {

    private static final Logger log = LoggerFactory.getLogger(FleetBalanceService.class);

    private final FleetBalanceRepository fleetBalanceRepository;
    private final FleetMovementRepository fleetMovementRepository;

    public FleetBalanceService(FleetBalanceRepository fleetBalanceRepository,
                               FleetMovementRepository fleetMovementRepository) {
        this.fleetBalanceRepository = fleetBalanceRepository;
        this.fleetMovementRepository = fleetMovementRepository;
    }

    /**
     * Lecture stricte — ne crée jamais implicitement. Un FleetBalance doit
     * exister via registerBalance (avec son numéro commercial, §7ter.3)
     * avant tout mouvement.
     */
    public FleetBalance getBalance(Device device, Operateur operateur) {
        return fleetBalanceRepository.findByDeviceAndOperateur(device, operateur)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FleetBalance", device.getId() + "/" + operateur.getCode()));
    }

    /**
     * Création explicite d'un solde flotte pour un couple (device, opérateur),
     * avec le numéro commercial obligatoire (cf. §7ter.3). Idempotent : si le
     * couple existe déjà, retourne le solde existant sans écraser son numéro
     * commercial.
     */
    @Transactional("transactionManager")
    public FleetBalance registerBalance(Device device, Operateur operateur, String commercialNumber) {
        if (commercialNumber == null || commercialNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Un numéro commercial est requis pour créer un solde flotte (§7ter.3)"
            );
        }
        return fleetBalanceRepository.findByDeviceAndOperateur(device, operateur)
                .orElseGet(() -> fleetBalanceRepository.save(
                        new FleetBalance(device, operateur, commercialNumber)));
    }

    /**
     * Débite le solde crédit. Verrouillage pessimiste explicite avant
     * lecture (findByIdForUpdate) : sans lui, deux débits concurrents
     * pourraient tous deux lire le même solde avant qu'aucun n'ait écrit,
     * et passer sous zéro malgré la vérification individuelle. Le
     * FleetMovement est TOUJOURS écrit dans la même transaction que la
     * mise à jour du solde — jamais l'un sans l'autre (CDC §7.7).
     */
    @Transactional("transactionManager")
    public FleetBalance debitCredit(UUID fleetBalanceId, BigDecimal amount,
                                    FleetMovementReason reason, String justification,
                                    UUID transactionId) {
        FleetBalance balance = lockBalance(fleetBalanceId);

        requirePositiveAmount(amount);
        if (balance.getCreditBalance().compareTo(amount) < 0) {
            throw new InsufficientFleetBalanceException(
                    fleetBalanceId, "credit", balance.getCreditBalance(), amount
            );
        }

        balance.setCreditBalance(balance.getCreditBalance().subtract(amount));
        recordMovement(balance, amount.negate(), reason, justification, transactionId);

        log.info("Débit crédit flotte [{}] : -{} ({})", fleetBalanceId, amount, reason);
        return balance;
    }

    @Transactional("transactionManager")
    public FleetBalance creditCredit(UUID fleetBalanceId, BigDecimal amount,
                                     FleetMovementReason reason, String justification,
                                     UUID transactionId) {
        FleetBalance balance = lockBalance(fleetBalanceId);

        requirePositiveAmount(amount);
        balance.setCreditBalance(balance.getCreditBalance().add(amount));
        recordMovement(balance, amount, reason, justification, transactionId);

        log.info("Crédit crédit flotte [{}] : +{} ({})", fleetBalanceId, amount, reason);
        return balance;
    }

    @Transactional("transactionManager")
    public FleetBalance debitWallet(UUID fleetBalanceId, BigDecimal amount,
                                    FleetMovementReason reason, String justification,
                                    UUID transactionId) {
        FleetBalance balance = lockBalance(fleetBalanceId);

        requirePositiveAmount(amount);
        if (balance.getWalletBalance().compareTo(amount) < 0) {
            throw new InsufficientFleetBalanceException(
                    fleetBalanceId, "wallet", balance.getWalletBalance(), amount
            );
        }

        balance.setWalletBalance(balance.getWalletBalance().subtract(amount));
        recordMovement(balance, amount.negate(), reason, justification, transactionId);

        log.info("Débit wallet flotte [{}] : -{} ({})", fleetBalanceId, amount, reason);
        return balance;
    }

    @Transactional("transactionManager")
    public FleetBalance creditWallet(UUID fleetBalanceId, BigDecimal amount,
                                     FleetMovementReason reason, String justification,
                                     UUID transactionId) {
        FleetBalance balance = lockBalance(fleetBalanceId);

        requirePositiveAmount(amount);
        balance.setWalletBalance(balance.getWalletBalance().add(amount));
        recordMovement(balance, amount, reason, justification, transactionId);

        log.info("Crédit wallet flotte [{}] : +{} ({})", fleetBalanceId, amount, reason);
        return balance;
    }

    /**
     * Ajustement manuel admin — justification obligatoire (CDC §7.7),
     * contrairement aux mouvements automatiques où elle est optionnelle
     * (le contexte est déjà porté par transactionId).
     */
    @Transactional("transactionManager")
    public FleetBalance manualAdjustment(UUID fleetBalanceId, BigDecimal signedAmount, String justification) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException(
                    "Un ajustement manuel de flotte exige une justification (CDC §7.7)"
            );
        }

        FleetBalance balance = lockBalance(fleetBalanceId);

        if (signedAmount.compareTo(BigDecimal.ZERO) >= 0) {
            balance.setCreditBalance(balance.getCreditBalance().add(signedAmount));
        } else {
            BigDecimal debitAmount = signedAmount.abs();
            if (balance.getCreditBalance().compareTo(debitAmount) < 0) {
                throw new InsufficientFleetBalanceException(
                        fleetBalanceId, "credit", balance.getCreditBalance(), debitAmount
                );
            }
            balance.setCreditBalance(balance.getCreditBalance().subtract(debitAmount));
        }

        recordMovement(balance, signedAmount, FleetMovementReason.MANUAL_CORRECTION, justification, null);

        log.info("Ajustement manuel flotte [{}] : {} — {}", fleetBalanceId, signedAmount, justification);
        return balance;
    }

    /**
     * Devices dont le solde (crédit ou wallet) passe sous le seuil configuré
     * (tnb.fleet.low-balance-threshold / critical-balance-threshold) — pour
     * alerte admin (CDC §7.7).
     */
    public List<FleetBalance> findBelowThreshold(BigDecimal threshold) {
        return fleetBalanceRepository.findBelowThreshold(threshold);
    }

    private FleetBalance lockBalance(UUID fleetBalanceId) {
        return fleetBalanceRepository.findByIdForUpdate(fleetBalanceId)
                .orElseThrow(() -> new ResourceNotFoundException("FleetBalance", fleetBalanceId));
    }

    private void recordMovement(FleetBalance balance, BigDecimal signedAmount,
                                FleetMovementReason reason, String justification,
                                UUID transactionId) {
        FleetMovement movement = new FleetMovement(
                balance, signedAmount, reason, justification, transactionId
        );
        fleetMovementRepository.save(movement);
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Le montant d'un mouvement de flotte doit être strictement positif"
            );
        }
    }
}