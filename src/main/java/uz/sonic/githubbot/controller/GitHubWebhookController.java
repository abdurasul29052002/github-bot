package uz.sonic.githubbot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.sonic.githubbot.model.PushEvent;
import uz.sonic.githubbot.service.GitHubWebhookService;
import uz.sonic.githubbot.service.TelegramNotificationService;

@RestController
@RequestMapping("/api/github")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubWebhookService webhookService;
    private final TelegramNotificationService notificationService;

    public GitHubWebhookController(
            GitHubWebhookService webhookService,
            TelegramNotificationService notificationService) {
        this.webhookService = webhookService;
        this.notificationService = notificationService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody PushEvent pushEvent) {

        if (!"push".equals(event)) {
            log.info("Ignoring non-push event: {}", event);
            return ResponseEntity.ok("Event ignored");
        }

        String branch = pushEvent.ref().replace("refs/heads/", "");
        String defaultBranch = pushEvent.repository().defaultBranch();
        if (!branch.equals(defaultBranch)) {
            log.info("Ignoring push to non-default branch: {}", branch);
            return ResponseEntity.ok("Non-default branch ignored");
        }

        String message = webhookService.formatPushMessage(pushEvent);
        notificationService.sendMessage(message);
        log.info("Push event processed for {}", pushEvent.repository().fullName());
        return ResponseEntity.ok("Notification sent");
    }
}
