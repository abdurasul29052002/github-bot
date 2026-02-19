package uz.sonic.githubbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uz.sonic.githubbot.config.GitHubWebhookProperties;

@SpringBootApplication
@EnableConfigurationProperties(GitHubWebhookProperties.class)
public class GithubBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GithubBotApplication.class, args);
    }

}
