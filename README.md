# Firebase Manager — Telegram Bot и Mini App

Telegram-бот на Kotlin с веб-приложением (Mini App): управление Firebase Realtime Database — добавление проектов по ключу, правила безопасности, поля в базе. Обновления приходят по **webhook**, основная работа — в **Mini App** в Telegram.

## Возможности

- **Webhook**: Telegram шлёт обновления на ваш HTTPS URL.
- **Mini App**: кнопка меню бота открывает веб-приложение; ключ, проекты, правила и поля редактируются в нём.
- В чате: `/start` — приветствие и кнопка «Открыть приложение»; остальные команды по желанию (можно оставить только редирект в Mini App).

## Требования

- Java 17+
- Токен бота от [@BotFather](https://t.me/BotFather)
- Публичный HTTPS URL для webhook (например через [ngrok](https://ngrok.com))

## Запуск (webhook + Mini App)

1. Создайте бота в [@BotFather](https://t.me/BotFather) и скопируйте токен.

2. Запустите туннель с HTTPS (например ngrok):
   ```bash
   ngrok http 8080
   ```
   Скопируйте выданный URL, например `https://xxxx.ngrok.io`.

3. Укажите переменные окружения и запустите приложение:
   ```bash
   export BOT_TOKEN=ваш_токен
   export WEBHOOK_BASE_URL=https://xxxx.ngrok.io
   # опционально: PORT=8080 (по умолчанию 8080)
   ./gradlew run
   ```
   Сервер поднимется на `0.0.0.0:8080`, зарегистрирует webhook и кнопку меню «Открыть приложение» с URL `{WEBHOOK_BASE_URL}/app`.

4. В Telegram откройте бота, нажмите кнопку меню (или отправьте `/start` и кнопку) и откройте Mini App. Добавьте ключ сервисного аккаунта, управляйте проектами, правилами и полями в корне RTDB.

## Использование

- **Ключ** — отправьте одним сообщением полный JSON сервисного ключа (как в Firebase Console → Project Settings → Service Accounts → Generate new private key). Проект будет добавлен, ссылка на БД подставится по умолчанию.

- **Команды:**
  - `/start` — приветствие и подсказки
  - `/projects` — список добавленных проектов
  - `/rules` — выбрать проект → показать правила → кнопка «Изменить правила»; после нажатия отправьте новый JSON правил сообщением
  - `/link` — выбрать проект → показать текущую ссылку на БД → кнопка «Изменить ссылку»; после нажатия отправьте новую ссылку (например `https://PROJECT_ID-default-rtdb.firebaseio.com/`)

## Конфигурация

- **Переменные окружения**: `BOT_TOKEN` (обязательно), `WEBHOOK_BASE_URL` (обязательно для webhook, HTTPS), `PORT` (по умолчанию 8080).
- Сессии и проекты хранятся **в памяти** (ключ по Telegram user id); таймаут 30 минут без активности. Ключи на диск не сохраняются.
- Для продакшена нужен постоянный HTTPS (не только ngrok) и при необходимости настройка Mini App в BotFather.

## Структура проекта

```
src/main/kotlin/com/firebasemanager/
├── Application.kt           # Точка входа, Ktor-сервер, webhook, /app, /api
├── bot/
│   ├── BotState.kt
│   ├── TelegramBot.kt      # processUpdate, setWebhook, setChatMenuButton
│   └── UserSession.kt
├── routes/
│   └── MiniAppApiRoutes.kt  # /api/projects, rules, data; проверка initData
├── webapp/
│   └── TelegramInitData.kt  # Проверка подписи Telegram Web App initData
├── firebase/, models/, services/
src/main/resources/static/app/
└── index.html               # Mini App: ключ, проекты, правила, поля
```

## Сборка

```bash
./gradlew build
```

Запуск без Gradle (после сборки):

```bash
BOT_TOKEN=ваш_токен ./build/install/firebase-manager/bin/firebase-manager
```

## Безопасность

- Храните `BOT_TOKEN` и ключи сервисных аккаунтов в безопасности.
- Рекомендуется не передавать ключи в чатах; бот рассчитан на личное использование в вашем чате с ботом.

## Лицензия

MIT
