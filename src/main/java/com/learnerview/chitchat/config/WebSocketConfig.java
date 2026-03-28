package com.learnerview.chitchat.config;

import com.learnerview.chitchat.security.WebSocketSubscriptionInterceptor;
import com.learnerview.chitchat.tenant.WebSocketTenantHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketTenantHandshakeInterceptor tenantHandshakeInterceptor;
    private final WebSocketSubscriptionInterceptor webSocketSubscriptionInterceptor;

    public WebSocketConfig(WebSocketTenantHandshakeInterceptor tenantHandshakeInterceptor,
                           WebSocketSubscriptionInterceptor webSocketSubscriptionInterceptor) {
        this.tenantHandshakeInterceptor = tenantHandshakeInterceptor;
        this.webSocketSubscriptionInterceptor = webSocketSubscriptionInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{10000, 10000})
              .setTaskScheduler(heartbeatScheduler()); 
        config.setApplicationDestinationPrefixes("/app");
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.scheduling.TaskScheduler heartbeatScheduler() {
        return new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketSubscriptionInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(tenantHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
