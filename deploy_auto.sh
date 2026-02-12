#!/bin/bash
# Автоматический деплой с паролем

SERVER="root@176.113.83.188"
PASSWORD="cFVPD6FhAF"
APP_DIR="/opt/firebase-manager"

# Функция для выполнения команд через SSH с паролем
ssh_with_pass() {
    expect <<EOF
set timeout 30
spawn ssh -o StrictHostKeyChecking=no $1
expect {
    "password:" {
        send "$2\r"
        exp_continue
    }
    "# " {
        send "$3\r"
        expect "# "
        send "exit\r"
    }
    "$ " {
        send "$3\r"
        expect "$ "
        send "exit\r"
    }
}
expect eof
EOF
}

# Функция для копирования файлов через SCP с паролем
scp_with_pass() {
    expect <<EOF
set timeout 30
spawn scp -o StrictHostKeyChecking=no $1 $2
expect {
    "password:" {
        send "$3\r"
        exp_continue
    }
    eof
}
EOF
}

echo "=== Создание директории на сервере ==="
ssh_with_pass "$SERVER" "$PASSWORD" "mkdir -p $APP_DIR && chmod 755 $APP_DIR"

echo ""
echo "=== Копирование JAR файла ==="
scp_with_pass "build/libs/firebase-manager.jar" "$SERVER:$APP_DIR/" "$PASSWORD"

echo ""
echo "=== Копирование скрипта запуска ==="
scp_with_pass "deploy/run.sh" "$SERVER:$APP_DIR/" "$PASSWORD"

echo ""
echo "=== Установка прав на скрипт ==="
ssh_with_pass "$SERVER" "$PASSWORD" "chmod +x $APP_DIR/run.sh"

echo ""
echo "=== Проверка Java на сервере ==="
ssh_with_pass "$SERVER" "$PASSWORD" "java -version || (apt update && apt install -y openjdk-17-jre)"

echo ""
echo "=== Деплой завершен ==="
echo ""
echo "На сервере выполните:"
echo "  ssh $SERVER"
echo "  cd $APP_DIR"
echo "  export BOT_TOKEN='ваш_токен'"
echo "  export WEBHOOK_BASE_URL='https://176.113.83.188:8443'"
echo "  ./run.sh start"
