package TNB.Switch.service;

import TNB.Switch.DTO.response.DeviceRegistrationResponse;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.DeviceStatus;
import TNB.Switch.exeption.IllegalStateTransitionException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.repository.OperateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final OperateurRepository operateurRepository;
    private final FleetBalanceService fleetBalanceService;

    public DeviceService(DeviceRepository deviceRepository,
                         OperateurRepository operateurRepository,
                         FleetBalanceService fleetBalanceService) {
        this.deviceRepository = deviceRepository;
        this.operateurRepository = operateurRepository;
        this.fleetBalanceService = fleetBalanceService;
    }

    /**
     * Enregistre un nouveau device avec ses opérateurs supportés et leurs numéros commerciaux.
     *
     * @param name Nom du device
     * @param operatorCommercialNumbers Map<UUID, String> : opérateurId -> numéro commercial
     * @return DeviceRegistrationResponse contenant l'ID, le pairingCode et le credential (affiché une seule fois)
     */
    @Transactional("transactionManager")
    public DeviceRegistrationResponse registerDevice(String name, Map<UUID, String> operatorCommercialNumbers) {
        if (operatorCommercialNumbers == null || operatorCommercialNumbers.isEmpty()) {
            throw new IllegalArgumentException("Au moins un opérateur doit être spécifié");
        }

        String pairingCode = generatePairingCode();
        String plainCredential = generateCredential();
        String credentialHash = BCrypt.hashpw(plainCredential, BCrypt.gensalt());

        Device device = new Device(name, pairingCode, credentialHash);

        // Ajouter les opérateurs supportés
        for (Map.Entry<UUID, String> entry : operatorCommercialNumbers.entrySet()) {
            Operateur operateur = operateurRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Operateur", entry.getKey()));
            device.addSupportedOperator(operateur);
        }

        Device saved = deviceRepository.save(device);

        // Créer les soldes flotte pour chaque opérateur avec son numéro commercial
        for (Map.Entry<UUID, String> entry : operatorCommercialNumbers.entrySet()) {
            Operateur operateur = operateurRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Operateur", entry.getKey()));
            fleetBalanceService.registerBalance(saved, operateur, entry.getValue());
        }

        log.info("Device enregistré [{}], pairingCode={}, opérateurs supportés={}",
                saved.getId(), pairingCode, operatorCommercialNumbers.size());

        return new DeviceRegistrationResponse(
                saved.getId(),
                pairingCode,
                plainCredential
        );
    }

    /**
     * Point d'entrée UNIQUE pour tout changement de statut.
     * Valide la transition via DeviceStatus.canTransitionTo() avant d'écrire.
     * Verrouillage pessimiste (findByIdForUpdate) : empêche deux threads
     * de transitionner le même device simultanément.
     */
    @Transactional("transactionManager")
    public Device transitionStatus(UUID deviceId, DeviceStatus target) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));

        DeviceStatus current = device.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Device", deviceId, current, target);
        }

        device.setStatus(target);
        log.info("Device [{}] transition : {} -> {}", deviceId, current, target);

        return device;
    }

    /**
     * Transitionne un device vers HOLDS (prise en charge d'une commande).
     * Appelé par RoutingService lors de l'affectation d'une commande.
     */
    @Transactional("transactionManager")
    public Device markHolds(UUID deviceId) {
        return transitionStatus(deviceId, DeviceStatus.HOLDS);
    }

    /**
     * Transitionne un device vers AVAILABLE (fin d'exécution USSD).
     * Appelé par DeviceStompHandler via CommandDispatcher après ACK.
     */
    @Transactional("transactionManager")
    public Device markAvailable(UUID deviceId) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));

        // Ne passer AVAILABLE que si on est HOLDS ou OFFLINE (reconnexion)
        if (device.getStatus() == DeviceStatus.HOLDS || device.getStatus() == DeviceStatus.OFFLINE) {
            return transitionStatus(deviceId, DeviceStatus.AVAILABLE);
        }
        return device;
    }

    /**
     * Met un device en pause (ne reçoit plus de commandes).
     * Action admin uniquement.
     */
    @Transactional("transactionManager")
    public Device pauseDevice(UUID deviceId) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));

        if (device.getStatus() == DeviceStatus.PAUSED) {
            log.debug("Device [{}] déjà en pause", deviceId);
            return device;
        }

        return transitionStatus(deviceId, DeviceStatus.PAUSED);
    }

    /**
     * Réactive un device en pause.
     * Action admin uniquement.
     */
    @Transactional("transactionManager")
    public Device resumeDevice(UUID deviceId) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));

        if (device.getStatus() != DeviceStatus.PAUSED) {
            throw new IllegalStateTransitionException("Device", deviceId, device.getStatus(), DeviceStatus.AVAILABLE);
        }

        return transitionStatus(deviceId, DeviceStatus.AVAILABLE);
    }

    /**
     * Enregistre un heartbeat. Si le device était OFFLINE, le fait
     * repasser AVAILABLE automatiquement — jamais vers HOLDS directement
     * (une commande en cours au moment d'une coupure devient orpheline
     * et repart via le timeout du RoutingService).
     */
    @Transactional("transactionManager")
    public void recordHeartbeat(UUID deviceId) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));

        device.setLastHeartbeat(Instant.now());

        if (device.getStatus() == DeviceStatus.OFFLINE) {
            transitionStatus(deviceId, DeviceStatus.AVAILABLE);
            log.info("Device [{}] reconnecté : OFFLINE -> AVAILABLE", deviceId);
        }
    }

    /**
     * Détecte et marque OFFLINE les devices dont le heartbeat a expiré.
     * Destiné à être appelé par DeviceHeartbeatWatchdog (@Scheduled).
     */
    @Transactional("transactionManager")
    public int markStaleDevicesOffline(int heartbeatTimeoutSeconds) {
        Instant threshold = Instant.now().minusSeconds(heartbeatTimeoutSeconds);
        var staleDevices = deviceRepository.findStaleHeartbeats(DeviceStatus.OFFLINE, threshold);

        int count = 0;
        for (Device device : staleDevices) {
            DeviceStatus current = device.getStatus();
            if (current.canTransitionTo(DeviceStatus.OFFLINE)) {
                device.setStatus(DeviceStatus.OFFLINE);
                log.warn("Device [{}] marqué OFFLINE (heartbeat expiré, statut: {})",
                        device.getId(), current);
                count++;
            }
        }

        log.info("{} device(s) marqué(s) OFFLINE (heartbeat expiré)", count);
        return count;
    }

    // ==================== MÉTHODES DE LECTURE ====================

    /**
     * Trouve un device par son ID.
     */
    public Device findById(UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));
    }

    /**
     * Trouve un device par son ID avec verrouillage pessimiste.
     */
    public Device findByIdForUpdate(UUID deviceId) {
        return deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceId));
    }

    /**
     * Trouve un device par son pairingCode (utilisé par DeviceStompAuthInterceptor).
     */
    public Device findByPairingCode(String pairingCode) {
        return deviceRepository.findByPairingCode(pairingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Device", pairingCode));
    }

    // ==================== MÉTHODES PRIVÉES ====================

    /**
     * Génère un pairingCode unique (16 bytes en Base64 URL-safe).
     */
    private String generatePairingCode() {
        SecureRandom random = new SecureRandom();
        return String.format("%05d", random.nextInt(100000));
    }


    /**
     * Génère un credential de 32 bytes en Base64 URL-safe.
     */
    private String generateCredential() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}