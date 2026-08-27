package TNB.Switch.config;


import TNB.Switch.security.DeviceStompAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${tnb.websocket.endpoint}")
    private String endpoint;

    @Value("${tnb.websocket.device-topic-prefix}")
    private String deviceTopicPrefix;

    @Value("${tnb.websocket.client-topic-prefix}")
    private String clientTopicPrefix;

    @Value("${tnb.websocket.admin-topic-prefix}")
    private String adminTopicPrefix;

    @Value("${tnb.websocket.allowed-origins}")
    private String allowedOrigins;

    @Value("${tnb.websocket.heartbeat-inbound}")
    private long heartbeatInbound;

    @Value("${tnb.websocket.heartbeat-outbound}")
    private long heartbeatOutbound;
    private final DeviceStompAuthInterceptor deviceStompAuthInterceptor;

    public WebSocketConfig(DeviceStompAuthInterceptor deviceStompAuthInterceptor) {
        this.deviceStompAuthInterceptor = deviceStompAuthInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(deviceStompAuthInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Préfixes de diffusion serveur -> clients (device/client/admin
        // déjà définis en config, jamais exploités jusqu'ici).
        registry.enableSimpleBroker(deviceTopicPrefix, clientTopicPrefix, adminTopicPrefix)
                .setHeartbeatValue(new long[]{heartbeatOutbound, heartbeatInbound});

        // Préfixe des messages entrants client -> serveur, routés vers
        // les méthodes @MessageMapping (DeviceStompHandler ci-dessous).
        registry.setApplicationDestinationPrefixes("/app");
    }
}