package uz.sonic.githubbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.forum.CreateForumTopic;
import org.telegram.telegrambots.meta.api.methods.forum.DeleteForumTopic;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.sonic.githubbot.repository.RepoTopicMappingRepository;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final TelegramClient telegramClient;
    private final String chatId;
    private final RepoTopicMappingRepository repoTopicMappingRepository;

    public TelegramNotificationService(
            TelegramClient telegramClient,
            @Value("${telegram.chat-id}") String chatId,
            RepoTopicMappingRepository repoTopicMappingRepository) {
        this.telegramClient = telegramClient;
        this.chatId = chatId;
        this.repoTopicMappingRepository = repoTopicMappingRepository;
    }

    public void sendMessageToRepo(String repoFullName, String text) {
        repoTopicMappingRepository.findByRepoFullName(repoFullName)
                .ifPresentOrElse(
                        mapping -> sendMessage(text, mapping.getTopicId()),
                        () -> log.warn("No topic mapping found for repo: {}", repoFullName)
                );
    }

    public void sendMessage(String text, Integer messageThreadId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .messageThreadId(messageThreadId)
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .build();
        try {
            telegramClient.execute(message);
            log.info("Telegram notification sent to topic {}", messageThreadId);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram notification to topic {}", messageThreadId, e);
        }
    }

    public ForumTopic createForumTopic(String name) throws TelegramApiException {
        CreateForumTopic createForumTopic = CreateForumTopic.builder()
                .chatId(chatId)
                .name(name)
                .build();
        return telegramClient.execute(createForumTopic);
    }

    public void deleteForumTopic(Integer messageThreadId) throws TelegramApiException {
        DeleteForumTopic deleteForumTopic = DeleteForumTopic.builder()
                .chatId(chatId)
                .messageThreadId(messageThreadId)
                .build();
        telegramClient.execute(deleteForumTopic);
    }
}
