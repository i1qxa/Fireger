# Деплой без Docker

Инструкция по запуску Firebase Manager на сервере без использования Docker.

## Требования

- **Java 17+** (JRE достаточно)
- **Порты 8080 и 8443** должны быть открыты
- Доступ по SSH к серверу

## Быстрый старт

### 1. Сборка JAR

На локальной машине:

```bash
./gradlew shadowJar
```

JAR файл будет создан в `build/libs/firebase-manager.jar`.

### 2. Автоматический деплой

Используйте скрипт `deploy.sh`:

```bash
chmod +x deploy.sh
./deploy.sh [SERVER_HOST] [USER]
```

Пример:
```bash
./deploy.sh 176.113.83.188 root
```

### 3. Ручной деплой

#### Шаг 1: Создание директории на сервере

**На сервере (через SSH):**
```bash
sudo mkdir -p /opt/firebase-manager
sudo chmod 755 /opt/firebase-manager
```

#### Шаг 2: Копирование файлов

**На локальной машине:**
```bash
# Сборка JAR (если еще не собрали)
./gradlew shadowJar

# Копирование на сервер
scp build/libs/firebase-manager.jar root@176.113.83.188:/opt/firebase-manager/
scp deploy/run.sh root@176.113.83.188:/opt/firebase-manager/
```

#### Шаг 3: Настройка и запуск на сервере

**На сервере:**
```bash
cd /opt/firebase-manager

# Установка Java (если не установлена)
sudo apt update
sudo apt install -y openjdk-17-jre

# Установка прав
chmod +x run.sh

# Редактирование переменных окружения в run.sh (или экспорт перед запуском)
export BOT_TOKEN="ваш_токен_бота"
export WEBHOOK_BASE_URL="https://176.113.83.188:8443"

# Запуск
./run.sh start

# Проверка статуса
./run.sh status

# Просмотр логов
tail -f firebase-manager.log
```

## Использование systemd (автозапуск)

### Установка service:

```bash
# На сервере
sudo cp deploy/firebase-manager.service /etc/systemd/system/
sudo nano /etc/systemd/system/firebase-manager.service  # Отредактируйте BOT_TOKEN и WEBHOOK_BASE_URL
sudo systemctl daemon-reload
sudo systemctl enable firebase-manager
sudo systemctl start firebase-manager
```

### Управление:

```bash
# Статус
sudo systemctl status firebase-manager

# Остановка
sudo systemctl stop firebase-manager

# Запуск
sudo systemctl start firebase-manager

# Перезапуск
sudo systemctl restart firebase-manager

# Логи
sudo journalctl -u firebase-manager -f
```

## Переменные окружения

- `BOT_TOKEN` - токен Telegram бота (обязательно)
- `WEBHOOK_BASE_URL` - базовый URL для webhook (например: `https://176.113.83.188:8443`)

Можно установить:
- В файле `run.sh` (для ручного запуска)
- В файле `firebase-manager.service` (для systemd)
- Экспортировать перед запуском: `export BOT_TOKEN="..."`

## Keystore

При первом запуске автоматически создается `keystore.jks` в рабочей директории приложения. Он будет содержать самоподписанный сертификат для доменов:
- `91.228.155.82`
- `176.113.83.188`
- `localhost`
- `127.0.0.1`

Если нужно добавить другие домены, отредактируйте `Application.kt` и пересоберите JAR.

## Проверка работы

1. **Проверка портов:**
   ```bash
   sudo netstat -tlnp | grep -E '8080|8443'
   ```

2. **Проверка логов:**
   ```bash
   tail -f /opt/firebase-manager/firebase-manager.log
   ```

3. **Тест HTTPS:**
   ```bash
   curl -k https://176.113.83.188:8443/app/
   ```

4. **Проверка webhook в Telegram:**
   - Отправьте сообщение боту
   - Проверьте логи на наличие ошибок

## Устранение проблем

### Java не найдена
```bash
sudo apt update
sudo apt install -y openjdk-17-jre
```

### Порт занят
```bash
# Найти процесс
sudo lsof -i :8443
# Остановить
sudo kill <PID>
```

### Ошибки TLS
- Убедитесь, что в `Application.kt` установлен `enabledProtocols = listOf("TLSv1.2")`
- Пересоберите JAR: `./gradlew clean shadowJar`

### Проблемы с правами
```bash
sudo chown -R $USER:$USER /opt/firebase-manager
chmod +x /opt/firebase-manager/run.sh
```

## Обновление

1. На локальной машине: `./gradlew shadowJar`
2. Скопировать новый JAR: `scp build/libs/firebase-manager.jar root@SERVER:/opt/firebase-manager/`
3. На сервере: `./run.sh restart` или `sudo systemctl restart firebase-manager`
