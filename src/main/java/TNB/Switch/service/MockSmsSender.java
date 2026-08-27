package TNB.Switch.service;

import TNB.Switch.impl.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implémentation de secours quand tnb.otp.sms-provider=mock — logue le
 * message au lieu de l'envoyer réellement. À remplacer par un vrai client
 * (Africa's Talking, Twilio...) une fois le provider choisi (CDC Annexe 15 pt 9).
 */
@Service
@Profile("dev")
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[MOCK SMS] à {} : {}", phoneNumber, message);
    }
}