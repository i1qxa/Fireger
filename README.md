# Firebase Manager — Telegram Bot и Mini App

Telegram-бот на Kotlin с веб-приложением (Mini App): управление Firebase Realtime Database — проекты по ключу сервисного аккаунта, правила безопасности, поля в корне БД. Роли доступа (админ / пользователь), шаблоны ссылок, история действий. Обновления приходят по **webhook**, основная работа — в **Mini App** в Telegram.

## Возможности

- **Webhook**: Telegram шлёт обновления на HTTPS URL; при старте приложение само регистрирует webhook и кнопку меню.
- **Mini App**: кнопка меню бота открывает веб-приложение. В приложении:
  - **Firebase** — список проектов (общий для всех с доступом), добавление по ключу, правила чтения/записи, поля в корне RTDB. В полях можно подставлять ссылки из шаблонов.
  - **Шаблоны** (только админ) — создание шаблонов «название + ссылка» для быстрой подстановки в поля.
  - **Пользователи** (только админ) — список пользователей, смена роли (админ / пользователь / нет доступа), удаление; при смене прав пользователю уходит уведомление в Telegram.
  - **История** (только админ) — лог изменений правил и полей по проектам.
- **Роли**: первый пользователь становится админом; остальные по умолчанию «нет доступа» и могут запросить доступ. Админ выдаёт права. Если в системе не остаётся ни одного админа, админом становится первый по id пользователь.
- **В чате бота**: `/start`, `/projects`, `/rules`, `/link` — работа с проектами и правилами.

## Требования

- Java 17+ (для локального запуска) или Docker
- Токен бота от [@BotFather](https://t.me/BotFather)
- Публичный HTTPS URL для webhook (ngrok, домен с TLS и т.п.)

## Запуск

### Вариант 1: Docker (рекомендуется)

1. Создайте бота в [@BotFather](https://t.me/BotFather), скопируйте токен.
2. Образ на Docker Hub: `i1qxa/firebase-manager:1.0`

   Скачать и запустить:

   ```bash
   docker pull i1qxa/firebase-manager:1.0
   docker run -d --name firebase-manager \
     -e BOT_TOKEN=ваш_токен \
     -e WEBHOOK_BASE_URL=https://ваш-публичный-url \
     -p 8080:8080 \
     -v firebase-manager-data:/app/data \
     i1qxa/firebase-manager:1.0
   ```

   Данные БД хранятся в volume `firebase-manager-data` и сохраняются при перезапуске контейнера.

### Вариант 2: Сборка образа из исходников

```bash
docker build -t firebase-manager:1.0 .
docker run -d --name firebase-manager \
  -e BOT_TOKEN=ваш_токен \
  -e WEBHOOK_BASE_URL=https://ваш-публичный-url \
  -p 8080:8080 \
  -v firebase-manager-data:/app/data \
  firebase-manager:1.0
```

### Вариант 3: Локально без Docker

1. Туннель HTTPS (например ngrok): `ngrok http 8080`, скопируйте URL.
2. Запуск:
   ```bash
   export BOT_TOKEN=ваш_токен
   export WEBHOOK_BASE_URL=https://ваш-url
   ./gradlew run
   ```
   Или после `./gradlew installDist`:
   ```bash
   BOT_TOKEN=... WEBHOOK_BASE_URL=... ./build/install/firebase-manager/bin/firebase-manager
   ```

Сервер слушает `0.0.0.0:8080`. Webhook и кнопка меню регистрируются при старте по `WEBHOOK_BASE_URL` (webhook: `{URL}/webhook`, Mini App: `{URL}/app`).

## Конфигурация

- **Переменные окружения**: `BOT_TOKEN` (обязательно), `WEBHOOK_BASE_URL` (обязательно, HTTPS), `PORT` (по умолчанию 8080).
- **Хранение**: данные в H2 (файловая БД в каталоге `data/` относительно рабочей директории): проекты, пользователи, история, шаблоны ссылок. При смене адреса сервера достаточно перезапустить приложение с новым `WEBHOOK_BASE_URL` — webhook обновится автоматически, в BotFather ничего менять не нужно.

## Сборка

```bash
./gradlew build
./gradlew installDist   # для запуска без Docker: build/install/firebase-manager/
```

## Структура проекта

```
src/main/kotlin/com/firebasemanager/
├── Application.kt              # Точка входа, Ktor, webhook, /app, /api
├── bot/
│   ├── BotState.kt
│   └── TelegramBot.kt         # setWebhook, setChatMenuButton, обработка команд
├── db/                         # H2, Exposed: проекты, пользователи, история, шаблоны
├── routes/
│   └── MiniAppApiRoutes.kt     # /api/me, projects, users, history, templates, rules, data
├── webapp/
│   └── TelegramInitData.kt    # Проверка подписи initData Mini App
├── firebase/, models/, services/
src/main/resources/static/app/
└── index.html                  # Mini App: вкладки Firebase, Шаблоны, Пользователи, История
```

## Безопасность

- Храните `BOT_TOKEN` и ключи сервисных аккаунтов в безопасности.
- Не передавайте токен и ключи в открытых чатах.

## Лицензия

MIT
