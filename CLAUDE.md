# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./mvnw clean package          # Build
./mvnw spring-boot:run        # Run locally
./mvnw test                   # Run tests
./mvnw package -DskipTests    # Build without tests
docker-compose up --build     # Run with Docker
```

## Tech Stack

- **Java 25**, **Spring Boot 4.0.2**, **Maven 3.9.12**
- **telegrambots-client / telegrambots-longpolling** v9.3.0 — Telegram Bot API
- **Spring Data JPA + H2** — file-based database at `./data/github-bot-db`
- DTOs use Java **records** with `@JsonIgnoreProperties(ignoreUnknown = true)`

## Architecture

The app bridges GitHub webhooks to Telegram forum topics, managed via an admin Telegram bot.

### GitHub Webhook Flow
`GitHub POST /api/github/webhook` → `WebhookSignatureFilter` (HMAC-SHA256 validation) → `GitHubWebhookController` (filters default-branch pushes only) → `GitHubWebhookService` (formats HTML message) → `TelegramNotificationService` (sends to mapped Telegram forum topic)

### Admin Bot Flow
`AdminBotService` (long-polling via `TelegramBotsLongPollingApplication`) listens for commands and inline keyboard callbacks from the admin chat. Manages `RepoTopicMapping` entities — each maps a `owner/repo` string to a Telegram forum topic thread ID. Supports both text commands (`/addrepo`, `/repos`, `/removerepo`, `/help`) and inline keyboard interactions (`/start`).

### Key Relationship
`RepoTopicMapping` (entity) connects everything: admin bot creates mappings, webhook flow looks up the topic ID by repo name to route notifications.

## Configuration

Environment variables loaded from `.env` file (via `spring.config.import: file:.env`):

| Variable | Purpose |
|----------|---------|
| `TELEGRAM_BOT_TOKEN` | Bot token for both notification and admin services |
| `TELEGRAM_BOT_USERNAME` | Bot username |
| `TELEGRAM_CHAT_ID` | Supergroup/forum chat for notifications |
| `TELEGRAM_ADMIN_CHAT_ID` | Chat ID to restrict admin commands |
| `GITHUB_WEBHOOK_SECRET` | HMAC-SHA256 secret for webhook validation |

## Conventions

- Telegram messages use **HTML** parse mode (not Markdown)
- Admin bot UI text is in **Uzbek**; button labels mix English/Uzbek
- Callback data format: `action:param` (e.g., `remove:owner/repo`, `confirm_remove:owner/repo`)
- `adminChatId` validates who can send commands; `chatId` is where bot sends messages (typically same supergroup)
