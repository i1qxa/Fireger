#!/bin/bash
set -e

SERVER="root@176.113.83.188"
PASSWORD="cFVPD6FhAF"
APP_DIR="/opt/firebase-manager"

# Проверка sshpass
if ! command -v sshpass &> /dev/null; then
    echo "⚠ sshpass не установлен. Установите его:"
    echo "  sudo apt install -y sshpass"
    echo ""
    echo "Или выполните команды из DEPLOY_COMMANDS.md вручную"
    exit 1
fi

echo "=== Деплой Firebase Manager ==="
echo ""

echo "1. Создание директории..."
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "mkdir -p $APP_DIR && chmod 755 $APP_DIR"
echo "✓ Директория создана"

echo ""
echo "2. Копирование JAR..."
if [ ! -f "build/libs/firebase-manager.jar" ]; then
    echo "✗ Ошибка: JAR не найден. Запустите: ./gradlew shadowJar"
    exit 1
fi
sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "build/libs/firebase-manager.jar" "$SERVER:$APP_DIR/"
echo "✓ JAR скопирован"

echo ""
echo "3. Копирование скрипта запуска..."
if [ ! -f "deploy/run.sh" ]; then
    echo "✗ Ошибка: Скрипт run.sh не найден"
    exit 1
fi
sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "deploy/run.sh" "$SERVER:$APP_DIR/"
echo "✓ Скрипт скопирован"

echo ""
echo "4. Установка прав..."
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "chmod +x $APP_DIR/run.sh"
echo "✓ Права установлены"

echo ""
echo "5. Проверка Java..."
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "java -version 2>&1 || (apt update -qq && apt install -y openjdk-17-jre > /dev/null 2>&1)" || echo "⚠ Предупреждение при проверке Java"

echo ""
echo "=== Деплой завершен успешно! ==="
echo ""
echo "Теперь подключитесь к серверу и запустите приложение:"
echo ""
echo "  ssh $SERVER"
echo "  cd $APP_DIR"
echo "  export BOT_TOKEN='8526577591:AAEfDi-GQ7EqNWaSAVoqZgXufgN1-Nc4-FU'"
echo "  export WEBHOOK_BASE_URL='https://176.113.83.188:8443'"
echo "  ./run.sh start"
echo ""
echo "Или используйте systemd service:"
echo "  sudo cp deploy/firebase-manager.service /etc/systemd/system/"
echo "  sudo nano /etc/systemd/system/firebase-manager.service  # Отредактируйте токен"
echo "  sudo systemctl daemon-reload"
echo "  sudo systemctl enable firebase-manager"
echo "  sudo systemctl start firebase-manager"
