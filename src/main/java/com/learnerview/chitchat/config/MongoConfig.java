package com.learnerview.chitchat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for Spring Data MongoDB
 * Enables automatic auditing and repository scanning
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.learnerview.chitchat.repositories")
@EnableMongoAuditing
public class MongoConfig {
    // Spring Data MongoDB auto-configuration will create mongoTemplate bean
    // No manual bean definition needed - Spring Boot handles it automatically
}
