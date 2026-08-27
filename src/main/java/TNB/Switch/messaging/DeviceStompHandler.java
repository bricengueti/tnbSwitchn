package TNB.Switch.messaging;

import TNB.Switch.DTO.request.CommandAckPayload;
import TNB.Switch.DTO.request.DeviceHeartbeatPayload;
import TNB.Switch.DTO.request.RawOperatorMessagePayload;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.Operateur;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.repository.OperateurRepository;
import TNB.Switch.security.TnbPrincipal;
import TNB.Switch.service.DeviceService;
import TNB.Switch.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * Point d'entrée réel des messages venant des devices (STOMP). Deux
 * destinations : /app/device/message (message opérateur brut, déclenche
 * ReconciliationService) et /app/device/ack (accusé de réception d'une
 * commande routée). L'acteur authentifié (TnbPrincipal.ActorType.DEVICE,
 * injecté par DeviceStompAuthInterceptor au CONNECT) donne l'identité du
 * device sans que celui-ci ait besoin de la répéter dans chaque payload.
 */
@Controller
public class DeviceStompHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceStompHandler.class);

    private final DeviceRepository deviceRepository;
    private final OperateurRepository operateurRepository;
    private final ReconciliationService reconciliationService;
    private final DeviceService deviceService;

    public DeviceStompHandler(DeviceRepository deviceRepository,
                              OperateurRepository operateurRepository,
                              ReconciliationService reconciliationService,
                              DeviceService deviceService) {
        this.deviceRepository = deviceRepository;
        this.operateurRepository = operateurRepository;
        this.reconciliationService = reconciliationService;
        this.deviceService = deviceService;
    }

    /**
     * Réception d'un message opérateur brut (SMS/notification USSD).
     * Déclenche l'étape 1 du pipeline de réconciliation (CDC §9.3bis) :
     * persistance immédiate, avant tout traitement IA.
     */
    @MessageMapping("/device/message")
    public void handleOperatorMessage(RawOperatorMessagePayload payload, Authentication authentication) {
        TnbPrincipal principal = extractDevicePrincipal(authentication);

        Device device = deviceRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Device", principal.id()));

        Operateur operateur = operateurRepository.findById(payload.operateurId())
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", payload.operateurId()));

        reconciliationService.receiveRawMessage(device, operateur, payload.rawContent());

        log.info("Message opérateur reçu du device [{}]", device.getId());
    }

    /**
     * ACK de réception d'une commande routée. Sert de confirmation que le
     * device a bien pris en charge la commande — pas encore exploité pour
     * de la logique métier avancée (ex. détecter un ACK jamais reçu comme
     * signal précoce de device défaillant), simple trace pour l'instant.
     */
    @MessageMapping("/device/ack")
    @SendToUser("/queue/ack-confirmed")
    public String handleCommandAck(CommandAckPayload payload, Authentication authentication) {
        TnbPrincipal principal = extractDevicePrincipal(authentication);

        log.info("ACK reçu du device [{}] pour la commande [{}] : reçu={}",
                principal.id(), payload.commandeId(), payload.received());

        deviceService.recordHeartbeat(principal.id());

        return "ACK enregistré";
    }
    /**
     * Heartbeat périodique (tnb.fleet.heartbeat-interval-seconds côté device).
     * Distinct de l'ACK de commande : un device AVAILABLE sans commande en
     * cours doit quand même prouver qu'il est vivant, sinon il serait
     * injustement marqué OFFLINE par markStaleDevicesOffline malgré une
     * connexion WebSocket toujours active mais simplement inactive.
     */
    @MessageMapping("/device/heartbeat")
    public void handleHeartbeat(DeviceHeartbeatPayload payload, Authentication authentication) {
        TnbPrincipal principal = extractDevicePrincipal(authentication);

        deviceService.recordHeartbeat(principal.id());

        log.debug("Heartbeat reçu du device [{}]", principal.id());
    }

    private TnbPrincipal extractDevicePrincipal(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TnbPrincipal principal)
                || principal.type() != TnbPrincipal.ActorType.DEVICE) {
            throw new IllegalStateException("Connexion non authentifiée comme device");
        }
        return principal;
    }
}