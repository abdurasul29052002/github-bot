package uz.sonic.githubbot.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.sonic.githubbot.entity.RepoTopicMapping;
import uz.sonic.githubbot.repository.RepoTopicMappingRepository;

import java.util.List;

@Service
public class AdminBotService implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(AdminBotService.class);

    private final String botToken;
    private final String adminChatId;
    private final TelegramNotificationService notificationService;
    private final RepoTopicMappingRepository repository;
    private TelegramBotsLongPollingApplication longPollingApp;

    public AdminBotService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.admin-chat-id}") String adminChatId,
            TelegramNotificationService notificationService,
            RepoTopicMappingRepository repository) {
        this.botToken = botToken;
        this.adminChatId = adminChatId;
        this.notificationService = notificationService;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            longPollingApp = new TelegramBotsLongPollingApplication();
            longPollingApp.registerBot(botToken, this);
            log.info("Admin bot long polling started");
        } catch (TelegramApiException e) {
            log.error("Failed to start admin bot long polling", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (longPollingApp != null) {
            try {
                longPollingApp.close();
                log.info("Admin bot long polling stopped");
            } catch (Exception e) {
                log.error("Error stopping admin bot long polling", e);
            }
        }
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message msg = update.getMessage();
        String chatId = msg.getChatId().toString();

        if (!chatId.equals(adminChatId)) {
            return;
        }

        String text = msg.getText().trim();
        Integer replyTopicId = msg.getMessageThreadId();

        try {
            if (text.startsWith("/addrepo ")) {
                handleAddRepo(text.substring(9).trim(), replyTopicId);
            } else if (text.equals("/repos")) {
                handleListRepos(replyTopicId);
            } else if (text.startsWith("/removerepo ")) {
                handleRemoveRepo(text.substring(12).trim(), replyTopicId);
            } else if (text.equals("/help")) {
                handleHelp(replyTopicId);
            }
        } catch (Exception e) {
            log.error("Error handling admin command: {}", text, e);
            sendReply("Error: " + e.getMessage(), replyTopicId);
        }
    }

    private void handleAddRepo(String repoFullName, Integer replyTopicId) {
        if (!repoFullName.matches("^[\\w.-]+/[\\w.-]+$")) {
            sendReply("Invalid format. Use: /addrepo owner/repo", replyTopicId);
            return;
        }

        if (repository.existsByRepoFullName(repoFullName)) {
            sendReply("Repository " + repoFullName + " is already configured.", replyTopicId);
            return;
        }

        try {
            ForumTopic topic = notificationService.createForumTopic(repoFullName);
            RepoTopicMapping mapping = new RepoTopicMapping(repoFullName, topic.getMessageThreadId());
            repository.save(mapping);
            sendReply("Added " + repoFullName + " with topic ID " + topic.getMessageThreadId(), replyTopicId);
            log.info("Added repo mapping: {} -> topic {}", repoFullName, topic.getMessageThreadId());
        } catch (TelegramApiException e) {
            log.error("Failed to create forum topic for {}", repoFullName, e);
            sendReply("Failed to create forum topic: " + e.getMessage(), replyTopicId);
        }
    }

    private void handleListRepos(Integer replyTopicId) {
        List<RepoTopicMapping> mappings = repository.findAll();
        if (mappings.isEmpty()) {
            sendReply("No repositories configured.", replyTopicId);
            return;
        }

        var sb = new StringBuilder("<b>Configured repositories:</b>\n\n");
        for (RepoTopicMapping m : mappings) {
            sb.append("- <code>").append(m.getRepoFullName()).append("</code> (topic ").append(m.getTopicId()).append(")\n");
        }
        sendReply(sb.toString(), replyTopicId);
    }

    private void handleRemoveRepo(String repoFullName, Integer replyTopicId) {
        var mapping = repository.findByRepoFullName(repoFullName);
        if (mapping.isEmpty()) {
            sendReply("Repository " + repoFullName + " not found.", replyTopicId);
            return;
        }

        repository.deleteByRepoFullName(repoFullName);

        try {
            notificationService.deleteForumTopic(mapping.get().getTopicId());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete forum topic for {}: {}", repoFullName, e.getMessage());
        }

        sendReply("Removed " + repoFullName, replyTopicId);
        log.info("Removed repo mapping: {}", repoFullName);
    }

    private void handleHelp(Integer replyTopicId) {
        String help = """
                <b>Admin Bot Commands:</b>

                /addrepo owner/repo - Add repository and create forum topic
                /repos - List all configured repositories
                /removerepo owner/repo - Remove repository and delete topic
                /help - Show this help message""";
        sendReply(help, replyTopicId);
    }

    private void sendReply(String htmlText, Integer messageThreadId) {
        notificationService.sendMessage(htmlText, messageThreadId);
    }
}
