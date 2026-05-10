package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.RegisterWebhookRequest;
import com.learnerview.chitchat.entities.WebhookSubscription;
import com.learnerview.chitchat.service.WebhookService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/integrations/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public WebhookSubscription register(@jakarta.validation.Valid @RequestBody RegisterWebhookRequest request) {
        return webhookService.register(request.getUrl(), request.getEvents(), request.getSecret());
    }

    @GetMapping
    public List<WebhookSubscription> list() {
        return webhookService.list();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        webhookService.delete(id);
    }
}
