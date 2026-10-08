package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.webhook.dto.WebhookResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/integrations/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    public record RegisterWebhookRequest(
            @NotBlank(message = "Webhook URL is required") String url,
            @Size(min = 1, message = "At least one event is required") Set<String> events,
            String secret
    ) {
    }

    public record UpdateWebhookRequest(String url, Set<String> events, Boolean active) {
    }

    @PutMapping("/{id}")
    public WebhookResponse update(@PathVariable String id, @RequestBody UpdateWebhookRequest request) {
        return webhookService.update(id, request.url(), request.events(), request.active());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookResponse register(@Valid @RequestBody RegisterWebhookRequest request) {
        return webhookService.register(request.url(), request.events(), request.secret());
    }

    @GetMapping
    public List<WebhookResponse> list() {
        return webhookService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        webhookService.delete(id);
    }
}
