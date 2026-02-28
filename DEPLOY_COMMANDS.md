# Команды для деплоя

Выполните эти команды последовательно на вашей локальной машине:

## 1. Установка sshpass (если еще не установлен)

```bash
sudo apt install -y sshpass
```

## 2. Создание директории на сервере

```bash
sshpass -p 'cFVPD6FhAF' ssh -o StrictHostKeyChecking=no root@176.113.83.188 "mkdir -p /opt/firebase-manager && chmod 755 /opt/firebase-manager"
```

## 3. Копирование JAR файла

```bash
sshpass -p 'cFVPD6FhAF' scp -o StrictHostKeyChecking=no build/libs/firebase-manager.jar root@176.113.83.188:/opt/firebase-manager/
```

## 4. Копирование скрипта запуска

```bash
sshpass -p 'cFVPD6FhAF' scp -o StrictHostKeyChecking=no deploy/run.sh root@176.113.83.188:/opt/firebase-manager/
```

## 5. Установка прав на скрипт

```bash
sshpass -p 'cFVPD6FhAF' ssh -o StrictHostKeyChecking=no root@176.113.83.188 "chmod +x /opt/firebase-manager/run.sh"
```

## 6. Проверка/установка Java на сервере

```bash
sshpass -p 'cFVPD6FhAF' ssh -o StrictHostKeyChecking=no root@176.113.83.188 "java -version || (apt update && apt install -y openjdk-17-jre)"
```

## 7. Запуск приложения на сервере

Подключитесь к серверу:
```bash
ssh root@176.113.83.188
```

На сервере выполните:
```bash
cd /opt/firebase-manager
export BOT_TOKEN="8526577591:AAEfDi-GQ7EqNWaSAVoqZgXufgN1-Nc4-FU"
export WEBHOOK_BASE_URL="https://176.113.83.188:8443"
./run.sh start
```

## Или все команды одной строкой:

```bash
# Установка sshpass (если нужно)
sudo apt install -y sshpass

# Деплой
cd ~/AndroidStudioProjects/firebase\ sample && \
sshpass -p 'cFVPD6FhAF' ssh -o StrictHostKeyChecking=no root@176.113.83.188 "mkdir -p /opt/firebase-manager && chmod 755 /opt/firebase-manager" && \
sshpass -p 'cFVPD6FhAF' scp -o StrictHostKeyChecking=no build/libs/firebase-manager.jar root@176.113.83.188:/opt/firebase-manager/ && \
sshpass -p 'cFVPD6FhAF' scp -o StrictHostKeyChecking=no deploy/run.sh root@176.113.83.188:/opt/firebase-manager/ && \
sshpass -p 'cFVPD6FhAF' ssh -o StrictHostKeyChecking=no root@176.113.83.188 "chmod +x /opt/firebase-manager/run.sh && java -version || (apt update && apt install -y openjdk-17-jre)" && \
echo "✓ Деплой завершен! Подключитесь к серверу и запустите: cd /opt/firebase-manager && export BOT_TOKEN='...' && export WEBHOOK_BASE_URL='https://176.113.83.188:8443' && ./run.sh start"
```
