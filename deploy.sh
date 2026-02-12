#!/bin/bash
# Скрипт для деплоя на сервер без Docker

set -e

SERVER_HOST="${1:-176.113.83.188}"
SERVER_USER="${2:-root}"
APP_DIR="/opt/firebase-manager"
JAR_NAME="firebase-manager.jar"
SERVICE_NAME="firebase-manager"

echo "=== Деплой Firebase Manager на $SERVER_USER@$SERVER_HOST ==="

# Проверка наличия JAR
if [ ! -f "build/libs/$JAR_NAME" ]; then
    echo "Ошибка: JAR файл не найден. Запустите: ./gradlew shadowJar"
    exit 1
fi

# Создание директории на сервере
echo "Создание директории на сервере..."
ssh "$SERVER_USER@$SERVER_HOST" "mkdir -p $APP_DIR"

# Копирование JAR
echo "Копирование JAR файла..."
scp "build/libs/$JAR_NAME" "$SERVER_USER@$SERVER_HOST:$APP_DIR/"

# Копирование скрипта запуска
echo "Копирование скрипта запуска..."
scp deploy/run.sh "$SERVER_USER@$SERVER_HOST:$APP_DIR/"

# Копирование systemd service (если нужно)
if [ -f "deploy/firebase-manager.service" ]; then
    echo "Копирование systemd service..."
    scp deploy/firebase-manager.service "$SERVER_USER@$SERVER_HOST:/tmp/"
    ssh "$SERVER_USER@$SERVER_HOST" "sudo mv /tmp/firebase-manager.service /etc/systemd/system/ && sudo systemctl daemon-reload"
fi

# Установка прав
ssh "$SERVER_USER@$SERVER_HOST" "chmod +x $APP_DIR/run.sh"

echo ""
echo "=== Деплой завершен ==="
echo ""
echo "На сервере выполните:"
echo "  cd $APP_DIR"
echo "  # Отредактируйте run.sh и установите BOT_TOKEN и WEBHOOK_BASE_URL"
echo "  ./run.sh"
echo ""
echo "Или используйте systemd service:"
echo "  sudo systemctl enable firebase-manager"
echo "  sudo systemctl start firebase-manager"
echo "  sudo systemctl status firebase-manager"
