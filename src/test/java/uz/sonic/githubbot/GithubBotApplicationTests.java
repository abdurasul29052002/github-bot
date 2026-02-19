package uz.sonic.githubbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "telegram.bot.token=test-token",
        "telegram.bot.username=test-bot",
        "telegram.chat-id=123456",
        "telegram.admin-chat-id=789012",
        "github.webhook.secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:testdb"
})
class GithubBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
