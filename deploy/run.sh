#!/bin/bash
# Скрипт запуска Firebase Manager без Docker

set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$APP_DIR/firebase-manager.jar"
PID_FILE="$APP_DIR/firebase-manager.pid"
LOG_FILE="$APP_DIR/firebase-manager.log"

# Переменные окружения (отредактируйте по необходимости)
export BOT_TOKEN="${BOT_TOKEN:-8526577591:AAEfDi-GQ7EqNWaSAVoqZgXufgN1-Nc4-FU}"
export WEBHOOK_BASE_URL="${WEBHOOK_BASE_URL:-https://176.113.83.188:8443}"

# Проверка Java
if ! command -v java &> /dev/null; then
    echo "Ошибка: Java не установлена. Установите Java 17+:"
    echo "  sudo apt update && sudo apt install -y openjdk-17-jre"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Ошибка: Требуется Java 17 или выше. Текущая версия: $JAVA_VERSION"
    exit 1
fi

# Проверка JAR
if [ ! -f "$JAR_FILE" ]; then
    echo "Ошибка: JAR файл не найден: $JAR_FILE"
    exit 1
fi

# Функция запуска
start() {
    if [ -f "$PID_FILE" ] && ps -p "$(cat "$PID_FILE")" > /dev/null 2>&1; then
        echo "Приложение уже запущено (PID: $(cat "$PID_FILE"))"
        return 1
    fi
    
    echo "Запуск Firebase Manager..."
    echo "BOT_TOKEN: ${BOT_TOKEN:0:20}..."
    echo "WEBHOOK_BASE_URL: $WEBHOOK_BASE_URL"
    
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    
    sleep 2
    if ps -p "$(cat "$PID_FILE")" > /dev/null 2>&1; then
        echo "Приложение запущено (PID: $(cat "$PID_FILE"))"
        echo "Логи: tail -f $LOG_FILE"
    else
        echo "Ошибка: Не удалось запустить приложение. Проверьте логи: $LOG_FILE"
        rm -f "$PID_FILE"
        return 1
    fi
}

# Функция остановки
stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "Приложение не запущено"
        return 1
    fi
    
    PID=$(cat "$PID_FILE")
    if ! ps -p "$PID" > /dev/null 2>&1; then
        echo "Процесс не найден. Удаляю PID файл."
        rm -f "$PID_FILE"
        return 1
    fi
    
    echo "Остановка приложения (PID: $PID)..."
    kill "$PID"
    
    # Ждем завершения
    for i in {1..10}; do
        if ! ps -p "$PID" > /dev/null 2>&1; then
            echo "Приложение остановлено"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
    done
    
    echo "Принудительная остановка..."
    kill -9 "$PID" 2>/dev/null || true
    rm -f "$PID_FILE"
    echo "Приложение остановлено"
}

# Функция статуса
status() {
    if [ -f "$PID_FILE" ] && ps -p "$(cat "$PID_FILE")" > /dev/null 2>&1; then
        PID=$(cat "$PID_FILE")
        echo "Приложение запущено (PID: $PID)"
        echo "Логи: tail -f $LOG_FILE"
        return 0
    else
        echo "Приложение не запущено"
        return 1
    fi
}

# Функция перезапуска
restart() {
    stop
    sleep 1
    start
}

# Обработка команд
case "${1:-start}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "Использование: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
