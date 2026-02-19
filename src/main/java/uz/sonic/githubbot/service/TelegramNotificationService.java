package uz.sonic.githubbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final TelegramClient telegramClient;
    private final String chatId;

    public TelegramNotificationService(
            TelegramClient telegramClient,
            @Value("${telegram.chat-id}") String chatId) {
        this.telegramClient = telegramClient;
        this.chatId = chatId;
    }

    public void sendMessage(String htmlText) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(htmlText)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();
        try {
            telegramClient.execute(message);
            log.info("Telegram notification sent successfully");
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram notification", e);
        }
    }
}
