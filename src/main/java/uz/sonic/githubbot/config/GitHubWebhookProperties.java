package uz.sonic.githubbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github.webhook")
public record GitHubWebhookProperties(String secret) {}
