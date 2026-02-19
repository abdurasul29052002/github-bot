package uz.sonic.githubbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "telegram.bot.token=test-token",
        "telegram.bot.username=test-bot",
        "telegram.chat-id=123456",
        "github.webhook.secret=test-secret"
})
class GithubBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
