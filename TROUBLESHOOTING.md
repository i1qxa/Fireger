# Решение проблем с запуском проекта

## Проверка ошибок

Если проект не запускается, выполните следующие шаги:

### 1. Проверьте логи ошибок

Запустите проект и посмотрите на вывод в консоли. Ошибки могут быть связаны с:

- **Отсутствующие зависимости**: Проверьте, что все зависимости загружены
- **Проблемы с Firebase**: Если нет проектов, это нормально
- **Проблемы с портом**: Порт 8080 может быть занят

### 2. Проверьте синхронизацию Gradle

В IntelliJ IDEA:
1. File → Settings → Build, Execution, Deployment → Build Tools → Gradle
2. Убедитесь, что выбран "Gradle wrapper"
3. Нажмите "Reload Gradle Project"

### 3. Очистите кэш и пересоберите

```bash
cd "/home/ivan/AndroidStudioProjects/firebase sample"
./gradlew clean build
```

### 4. Проверьте наличие файла projects.json

Убедитесь, что файл `projects.json` существует в корне проекта:
```json
{
  "projects": []
}
```

### 5. Проверьте Java версию

Убедитесь, что используется Java 17:
```bash
java -version
```

### 6. Запуск через командную строку

Попробуйте запустить через Gradle:
```bash
./gradlew run
```

### 7. Типичные ошибки

#### Ошибка: "Port 8080 is already in use"
**Решение**: Измените порт в `Application.kt`:
```kotlin
embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
```

#### Ошибка: "Could not find or load main class"
**Решение**: Убедитесь, что `mainClass` в `build.gradle.kts` правильный:
```kotlin
mainClass.set("com.firebasemanager.ApplicationKt")
```

#### Ошибка при инициализации Firebase
**Решение**: Это нормально, если нет добавленных проектов. Приложение должно запуститься даже без проектов.

### 8. Запуск в режиме отладки

В IntelliJ IDEA:
1. Откройте `Application.kt`
2. Нажмите правой кнопкой на функцию `main()`
3. Выберите "Debug 'ApplicationKt'"
4. Посмотрите на вывод в консоли

### 9. Проверка зависимостей

Убедитесь, что все зависимости загружены:
```bash
./gradlew dependencies
```

## Если ничего не помогает

1. Удалите папку `.gradle` в корне проекта
2. Удалите папку `build`
3. В IntelliJ IDEA: File → Invalidate Caches → Invalidate and Restart
4. После перезапуска попробуйте снова
