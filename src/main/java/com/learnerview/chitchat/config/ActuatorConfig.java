package com.learnerview.chitchat.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ActuatorConfig implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> details = new HashMap<>();
        details.put("application", "ChitChat");
        details.put("version", "1.0.0");
        details.put("description", "Real-time chat application with WebSocket support");
        details.put("features", new String[]{
            "JWT Authentication",
            "Real-time Messaging",
            "WebSocket Support",
            "Content Moderation",
            "Rate Limiting",
            "Typing Indicators",
            "Online Presence"
        });
        details.put("technologies", new String[]{
            "Java 17",
            "Spring Boot 3.2",
            "MongoDB",
            "WebSocket",
            "JWT",
            "Swagger/OpenAPI 3.0"
        });
        details.put("api", new String[]{
            "REST API",
            "WebSocket API",
            "Swagger UI"
        });
        
        builder.withDetail("application", details);
    }
}
