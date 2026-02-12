#!/usr/bin/env python3
import subprocess
import sys
import os

SERVER = "root@176.113.83.188"
PASSWORD = "cFVPD6FhAF"
APP_DIR = "/opt/firebase-manager"

def run_ssh_command(cmd):
    """Выполняет команду через SSH с паролем"""
    ssh_cmd = f"ssh -o StrictHostKeyChecking=no {SERVER} '{cmd}'"
    proc = subprocess.Popen(
        ['ssh', '-o', 'StrictHostKeyChecking=no', SERVER, cmd],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    # Пароль передается через stdin при первом запросе
    stdout, stderr = proc.communicate(input=f"{PASSWORD}\n")
    return proc.returncode == 0, stdout, stderr

def run_scp(local_file, remote_path):
    """Копирует файл через SCP с паролем"""
    scp_cmd = ['scp', '-o', 'StrictHostKeyChecking=no', local_file, f"{SERVER}:{remote_path}"]
    proc = subprocess.Popen(
        scp_cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    stdout, stderr = proc.communicate(input=f"{PASSWORD}\n")
    return proc.returncode == 0, stdout, stderr

print("=== Создание директории на сервере ===")
success, out, err = run_ssh_command(f"mkdir -p {APP_DIR} && chmod 755 {APP_DIR}")
if success:
    print("✓ Директория создана")
else:
    print(f"✗ Ошибка: {err}")

print("\n=== Копирование JAR файла ===")
if os.path.exists("build/libs/firebase-manager.jar"):
    success, out, err = run_scp("build/libs/firebase-manager.jar", f"{APP_DIR}/")
    if success:
        print("✓ JAR скопирован")
    else:
        print(f"✗ Ошибка: {err}")
else:
    print("✗ JAR файл не найден. Запустите: ./gradlew shadowJar")

print("\n=== Копирование скрипта запуска ===")
if os.path.exists("deploy/run.sh"):
    success, out, err = run_scp("deploy/run.sh", f"{APP_DIR}/")
    if success:
        print("✓ Скрипт скопирован")
    else:
        print(f"✗ Ошибка: {err}")
else:
    print("✗ Скрипт run.sh не найден")

print("\n=== Установка прав ===")
success, out, err = run_ssh_command(f"chmod +x {APP_DIR}/run.sh")
if success:
    print("✓ Права установлены")
else:
    print(f"✗ Ошибка: {err}")

print("\n=== Деплой завершен ===")
print(f"\nНа сервере выполните:")
print(f"  ssh {SERVER}")
print(f"  cd {APP_DIR}")
print(f"  export BOT_TOKEN='ваш_токен'")
print(f"  export WEBHOOK_BASE_URL='https://176.113.83.188:8443'")
print(f"  ./run.sh start")
