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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.forum.ForumTopic;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.sonic.githubbot.entity.RepoTopicMapping;
import uz.sonic.githubbot.repository.RepoTopicMappingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminBotService implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(AdminBotService.class);

    private final String botToken;
    private final String adminChatId;
    private final TelegramClient telegramClient;
    private final TelegramNotificationService notificationService;
    private final RepoTopicMappingRepository repository;
    private TelegramBotsLongPollingApplication longPollingApp;

    private final Map<Long, Boolean> waitingForRepoName = new ConcurrentHashMap<>();

    public AdminBotService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.admin-chat-id}") String adminChatId,
            TelegramClient telegramClient,
            TelegramNotificationService notificationService,
            RepoTopicMappingRepository repository) {
        this.botToken = botToken;
        this.adminChatId = adminChatId;
        this.telegramClient = telegramClient;
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
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

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
            if (text.equals("/start")) {
                handleStart(replyTopicId);
            } else if (text.startsWith("/addrepo ")) {
                handleAddRepo(text.substring(9).trim(), replyTopicId);
            } else if (text.equals("/repos")) {
                handleListRepos(replyTopicId);
            } else if (text.startsWith("/removerepo ")) {
                handleRemoveRepo(text.substring(12).trim(), replyTopicId);
            } else if (text.equals("/help")) {
                handleHelp(replyTopicId);
            } else if (waitingForRepoName.remove(msg.getChatId()) != null) {
                handleAddRepo(text, replyTopicId);
                sendMessageWithKeyboard("Asosiy menyu:", buildMainMenu(), replyTopicId);
            }
        } catch (Exception e) {
            log.error("Error handling admin command: {}", text, e);
            sendReply("Error: " + e.getMessage(), replyTopicId);
        }
    }

    private void handleStart(Integer replyTopicId) {
        sendMessageWithKeyboard("Buyruqni tanlang:", buildMainMenu(), replyTopicId);
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        if (!(callbackQuery.getMessage() instanceof Message message)) {
            return;
        }

        String chatId = message.getChatId().toString();
        if (!chatId.equals(adminChatId)) {
            return;
        }

        String data = callbackQuery.getData();
        Integer messageId = message.getMessageId();

        try {
            answerCallbackQuery(callbackQuery.getId());

            switch (data) {
                case "add_repo" -> {
                    waitingForRepoName.put(message.getChatId(), true);
                    editMessage("Repo nomini <code>owner/repo</code> formatida yuboring:", messageId, null);
                }
                case "list_repos" -> handleListReposCallback(messageId);
                case "remove_repo" -> handleRemoveRepoCallback(messageId);
                case "cancel_remove" -> editMessage("Bekor qilindi.", messageId, buildMainMenu());
                case "main_menu" -> editMessage("Buyruqni tanlang:", messageId, buildMainMenu());
                default -> {
                    if (data.startsWith("remove:")) {
                        handleRemoveConfirmation(data.substring(7), messageId);
                    } else if (data.startsWith("confirm_remove:")) {
                        handleConfirmedRemove(data.substring(15), messageId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", data, e);
        }
    }

    private void handleListReposCallback(Integer messageId) {
        List<RepoTopicMapping> mappings = repository.findAll();
        if (mappings.isEmpty()) {
            editMessage("Hech qanday repo sozlanmagan.", messageId, buildMainMenu());
            return;
        }

        var sb = new StringBuilder("<b>Sozlangan repolar:</b>\n\n");
        for (RepoTopicMapping m : mappings) {
            sb.append("- <code>").append(m.getRepoFullName()).append("</code> (topic ").append(m.getTopicId()).append(")\n");
        }
        editMessage(sb.toString(), messageId, buildMainMenu());
    }

    private void handleRemoveRepoCallback(Integer messageId) {
        List<RepoTopicMapping> mappings = repository.findAll();
        if (mappings.isEmpty()) {
            editMessage("O'chirish uchun repo yo'q.", messageId, buildMainMenu());
            return;
        }

        var rows = new ArrayList<>(mappings.stream()
                .map(m -> new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(m.getRepoFullName())
                                .callbackData("remove:" + m.getRepoFullName())
                                .build()))
                .toList());
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("◀️ Ortga")
                        .callbackData("main_menu")
                        .build()));

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();
        editMessage("O'chirish uchun repo tanlang:", messageId, keyboard);
    }

    private void handleRemoveConfirmation(String repoName, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("✅ Ha")
                                .callbackData("confirm_remove:" + repoName)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("❌ Yo'q")
                                .callbackData("cancel_remove")
                                .build()))
                .build();
        editMessage("<b>" + repoName + "</b> ni o'chirishga ishonchingiz komilmi?", messageId, keyboard);
    }

    private void handleConfirmedRemove(String repoName, Integer messageId) {
        var mapping = repository.findByRepoFullName(repoName);
        if (mapping.isEmpty()) {
            editMessage("Repository " + repoName + " topilmadi.", messageId, buildMainMenu());
            return;
        }

        repository.deleteByRepoFullName(repoName);

        try {
            notificationService.deleteForumTopic(mapping.get().getTopicId());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete forum topic for {}: {}", repoName, e.getMessage());
        }

        editMessage("✅ <b>" + repoName + "</b> o'chirildi.", messageId, buildMainMenu());
        log.info("Removed repo mapping: {}", repoName);
    }

    // --- Text command handlers (backward compatibility) ---

    private void handleAddRepo(String repoFullName, Integer replyTopicId) {
        if (!repoFullName.matches("^[\\w.-]+/[\\w.-]+$")) {
            sendReply("Noto'g'ri format. <code>owner/repo</code> formatida yuboring.", replyTopicId);
            return;
        }

        if (repository.existsByRepoFullName(repoFullName)) {
            sendReply("Repository " + repoFullName + " allaqachon sozlangan.", replyTopicId);
            return;
        }

        try {
            ForumTopic topic = notificationService.createForumTopic(repoFullName);
            RepoTopicMapping mapping = new RepoTopicMapping(repoFullName, topic.getMessageThreadId());
            repository.save(mapping);
            sendReply("✅ <b>" + repoFullName + "</b> qo'shildi (topic ID: " + topic.getMessageThreadId() + ")", replyTopicId);
            log.info("Added repo mapping: {} -> topic {}", repoFullName, topic.getMessageThreadId());
        } catch (TelegramApiException e) {
            log.error("Failed to create forum topic for {}", repoFullName, e);
            sendReply("Forum topic yaratishda xato: " + e.getMessage(), replyTopicId);
        }
    }

    private void handleListRepos(Integer replyTopicId) {
        List<RepoTopicMapping> mappings = repository.findAll();
        if (mappings.isEmpty()) {
            sendReply("Hech qanday repo sozlanmagan.", replyTopicId);
            return;
        }

        var sb = new StringBuilder("<b>Sozlangan repolar:</b>\n\n");
        for (RepoTopicMapping m : mappings) {
            sb.append("- <code>").append(m.getRepoFullName()).append("</code> (topic ").append(m.getTopicId()).append(")\n");
        }
        sendReply(sb.toString(), replyTopicId);
    }

    private void handleRemoveRepo(String repoFullName, Integer replyTopicId) {
        var mapping = repository.findByRepoFullName(repoFullName);
        if (mapping.isEmpty()) {
            sendReply("Repository " + repoFullName + " topilmadi.", replyTopicId);
            return;
        }

        repository.deleteByRepoFullName(repoFullName);

        try {
            notificationService.deleteForumTopic(mapping.get().getTopicId());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete forum topic for {}: {}", repoFullName, e.getMessage());
        }

        sendReply("✅ <b>" + repoFullName + "</b> o'chirildi.", replyTopicId);
        log.info("Removed repo mapping: {}", repoFullName);
    }

    private void handleHelp(Integer replyTopicId) {
        String help = """
                <b>Admin Bot Buyruqlari:</b>

                /start - Asosiy menyuni ko'rsatish
                /addrepo owner/repo - Repo qo'shish va forum topic yaratish
                /repos - Barcha sozlangan repolarni ko'rsatish
                /removerepo owner/repo - Reponi o'chirish va topicni yo'q qilish
                /help - Ushbu yordam xabarini ko'rsatish""";
        sendReply(help, replyTopicId);
    }

    // --- Helper methods ---

    private InlineKeyboardMarkup buildMainMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📁 Add Repo").callbackData("add_repo").build()))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📋 List Repos").callbackData("list_repos").build()))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🗑 Remove Repo").callbackData("remove_repo").build()))
                .build();
    }

    private void sendMessageWithKeyboard(String htmlText, InlineKeyboardMarkup keyboard, Integer messageThreadId) {
        SendMessage message = SendMessage.builder()
                .chatId(adminChatId)
                .text(htmlText)
                .messageThreadId(messageThreadId)
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message with keyboard", e);
        }
    }

    private void editMessage(String htmlText, Integer messageId, InlineKeyboardMarkup keyboard) {
        var builder = EditMessageText.builder()
                .chatId(adminChatId)
                .messageId(messageId)
                .text(htmlText)
                .parseMode("HTML");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("Failed to edit message", e);
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query", e);
        }
    }

    private void sendReply(String htmlText, Integer messageThreadId) {
        notificationService.sendMessage(htmlText, messageThreadId);
    }
}
