# Firebase Manager — Telegram Bot

Telegram-бот на Kotlin для управления Firebase Realtime Database: добавление проектов по ключу, просмотр и изменение правил безопасности и ссылок на базу.

## Возможности

- Добавление проекта: отправьте ключ (JSON сервисного аккаунта) текстовым сообщением
- Просмотр и изменение правил безопасности Realtime Database
- Просмотр и изменение ссылки на базу (Database URL)
- Список проектов по команде

## Требования

- Java 17+
- Токен бота от [@BotFather](https://t.me/BotFather)

## Запуск

1. Создайте бота в Telegram через [@BotFather](https://t.me/BotFather) и скопируйте токен.

2. Укажите токен в переменной окружения и запустите:
   ```bash
   export BOT_TOKEN=ваш_токен
   ./gradlew run
   ```
   Или в одной строке:
   ```bash
   BOT_TOKEN=ваш_токен ./gradlew run
   ```

3. В Telegram найдите бота и отправьте `/start`.

## Использование

- **Ключ** — отправьте одним сообщением полный JSON сервисного ключа (как в Firebase Console → Project Settings → Service Accounts → Generate new private key). Проект будет добавлен, ссылка на БД подставится по умолчанию.

- **Команды:**
  - `/start` — приветствие и подсказки
  - `/projects` — список добавленных проектов
  - `/rules` — выбрать проект → показать правила → кнопка «Изменить правила»; после нажатия отправьте новый JSON правил сообщением
  - `/link` — выбрать проект → показать текущую ссылку на БД → кнопка «Изменить ссылку»; после нажатия отправьте новую ссылку (например `https://PROJECT_ID-default-rtdb.firebaseio.com/`)

## Конфигурация

- Проекты хранятся в `projects.json` в рабочей директории.
- Ключи сохраняются в `config/service-accounts/`.
- Эти пути добавлены в `.gitignore` — не коммитьте их в репозиторий.

## Структура проекта

```
src/main/kotlin/com/firebasemanager/
├── Application.kt           # Точка входа, запуск бота
├── bot/
│   ├── BotState.kt         # Состояние диалога (ожидание правил/ссылки)
│   └── TelegramBot.kt     # Обработка команд и сообщений
├── config/
│   └── AppConfig.kt        # Загрузка/сохранение projects.json
├── firebase/
│   ├── FirebaseManager.kt # Инициализация Firebase, валидация ключей
│   └── FirebaseProject.kt
├── models/                 # Модели (ProjectConfig и др.)
└── services/               # ProjectService, RulesService
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
