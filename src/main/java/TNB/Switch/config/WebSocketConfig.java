package TNB.Switch.config;

import TNB.Switch.security.DeviceStompAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${tnb.websocket.endpoint:/ws}")
    private String endpoint;

    @Value("${tnb.websocket.device-topic-prefix:/topic/device}")
    private String deviceTopicPrefix;

    @Value("${tnb.websocket.client-topic-prefix:/topic/client}")
    private String clientTopicPrefix;

    @Value("${tnb.websocket.admin-topic-prefix:/topic/admin}")
    private String adminTopicPrefix;

    @Value("${tnb.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${tnb.websocket.heartbeat-inbound:10000}")
    private long heartbeatInbound;

    @Value("${tnb.websocket.heartbeat-outbound:10000}")
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
        // ✅ Ajouter un TaskScheduler pour les heartbeats
        registry.enableSimpleBroker(deviceTopicPrefix, clientTopicPrefix, adminTopicPrefix)
                .setHeartbeatValue(new long[]{heartbeatOutbound, heartbeatInbound})
                .setTaskScheduler(heartbeatTaskScheduler());  // ✅ Ajouter cette ligne

        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * ✅ Bean TaskScheduler pour les heartbeats WebSocket.
     * Obligatoire quand on configure des heartbeats.
     */
    @Bean
    public ThreadPoolTaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}