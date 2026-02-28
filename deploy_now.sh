#!/bin/bash
# Быстрый деплой с использованием sshpass (если доступен)

SERVER="root@176.113.83.188"
PASSWORD="cFVPD6FhAF"
APP_DIR="/opt/firebase-manager"

# Проверка sshpass
if ! command -v sshpass &> /dev/null; then
    echo "Установка sshpass..."
    sudo apt install -y sshpass || {
        echo "Не удалось установить sshpass автоматически."
        echo "Выполните вручную: sudo apt install -y sshpass"
        echo "Затем запустите этот скрипт снова."
        exit 1
    }
fi

echo "=== Создание директории на сервере ==="
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "mkdir -p $APP_DIR && chmod 755 $APP_DIR" || exit 1

echo ""
echo "=== Копирование JAR файла ==="
if [ ! -f "build/libs/firebase-manager.jar" ]; then
    echo "Ошибка: JAR файл не найден. Запустите: ./gradlew shadowJar"
    exit 1
fi
sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "build/libs/firebase-manager.jar" "$SERVER:$APP_DIR/" || exit 1

echo ""
echo "=== Копирование скрипта запуска ==="
if [ ! -f "deploy/run.sh" ]; then
    echo "Ошибка: Скрипт run.sh не найден"
    exit 1
fi
sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no "deploy/run.sh" "$SERVER:$APP_DIR/" || exit 1

echo ""
echo "=== Установка прав ==="
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "chmod +x $APP_DIR/run.sh" || exit 1

echo ""
echo "=== Проверка Java на сервере ==="
sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no "$SERVER" "java -version 2>&1 || (apt update && apt install -y openjdk-17-jre)" || echo "Предупреждение: Не удалось проверить/установить Java"

echo ""
echo "=== Деплой завершен успешно! ==="
echo ""
echo "На сервере выполните:"
echo "  ssh $SERVER"
echo "  cd $APP_DIR"
echo "  export BOT_TOKEN='ваш_токен'"
echo "  export WEBHOOK_BASE_URL='https://176.113.83.188:8443'"
echo "  ./run.sh start"
