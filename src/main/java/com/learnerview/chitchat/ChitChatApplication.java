package com.learnerview.chitchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class ChitChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChitChatApplication.class, args);
    }
}

