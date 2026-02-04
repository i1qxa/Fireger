# Настройка Gradle для решения проблемы сборки

Если вы получили ошибку `org.gradle.api.internal.HasConvention`, выполните следующие шаги:

## Решение 1: Использование Gradle Wrapper через IntelliJ IDEA

1. Откройте проект в IntelliJ IDEA
2. File → Settings → Build, Execution, Deployment → Build Tools → Gradle
3. Убедитесь, что выбран "Gradle wrapper" (не "Use Gradle from")
4. Нажмите OK
5. В правом верхнем углу нажмите на иконку Gradle и выберите "Reload Gradle Project"

## Решение 2: Обновление через командную строку

Если у вас установлен Gradle:

```bash
cd "/home/ivan/AndroidStudioProjects/firebase sample"
gradle wrapper --gradle-version 8.10.2
./gradlew clean build
```

## Решение 3: Ручная настройка

1. Убедитесь, что файл `gradle/wrapper/gradle-wrapper.properties` существует
2. В IntelliJ IDEA: File → Invalidate Caches → Invalidate and Restart
3. После перезапуска IDEA автоматически скачает Gradle wrapper

## Текущие версии в проекте

- Kotlin: 1.9.22
- Gradle: 8.10.2 (через wrapper)
- Java: 17

## Если проблема сохраняется

Попробуйте удалить кэш Gradle:
```bash
rm -rf ~/.gradle/caches
```

Затем в IntelliJ IDEA:
1. File → Invalidate Caches → Invalidate and Restart
2. После перезапуска попробуйте собрать проект снова
