package TNB.Switch.config;

import TNB.Switch.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Détecte périodiquement les devices dont le heartbeat a expiré
 * (tnb.fleet.heartbeat-timeout-seconds) et les marque OFFLINE. Complète
 * le heartbeat STOMP (DeviceStompHandler.handleHeartbeat) : sans ce job,
 * une coupure brutale de connexion (crash device, perte réseau sans
 * fermeture propre du WebSocket) laisserait le device AVAILABLE
 * indéfiniment côté backend.
 */
@Component
public class DeviceHeartbeatWatchdog {

    private static final Logger log = LoggerFactory.getLogger(DeviceHeartbeatWatchdog.class);

    private final DeviceService deviceService;
    private final int heartbeatTimeoutSeconds;

    public DeviceHeartbeatWatchdog(
            DeviceService deviceService,
            @Value("${tnb.fleet.heartbeat-timeout-seconds}") int heartbeatTimeoutSeconds) {
        this.deviceService = deviceService;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    // Vérifie deux fois plus souvent que le timeout lui-même, pour une
    // détection réactive sans excès de charge sur la base.
    @Scheduled(fixedRateString = "#{${tnb.fleet.heartbeat-timeout-seconds} * 500}")
    public void checkStaleDevices() {
        deviceService.markStaleDevicesOffline(heartbeatTimeoutSeconds);
    }
}