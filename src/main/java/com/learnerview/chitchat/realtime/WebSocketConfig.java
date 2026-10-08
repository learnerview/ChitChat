package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.security.JwtHandshakeHandler;
import com.learnerview.chitchat.common.tenancy.WebSocketTenantHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketTenantHandshakeInterceptor tenantHandshakeInterceptor;
    private final ConnectionLimitHandshakeInterceptor connectionLimitHandshakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;
    private final WebSocketSubscriptionInterceptor subscriptionInterceptor;

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    public WebSocketConfig(WebSocketTenantHandshakeInterceptor tenantHandshakeInterceptor,
                           ConnectionLimitHandshakeInterceptor connectionLimitHandshakeInterceptor,
                           JwtHandshakeHandler jwtHandshakeHandler,
                           WebSocketSubscriptionInterceptor subscriptionInterceptor) {
        this.tenantHandshakeInterceptor = tenantHandshakeInterceptor;
        this.connectionLimitHandshakeInterceptor = connectionLimitHandshakeInterceptor;
        this.jwtHandshakeHandler = jwtHandshakeHandler;
        this.subscriptionInterceptor = subscriptionInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(jwtHandshakeHandler)
                .addInterceptors(connectionLimitHandshakeInterceptor, tenantHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
