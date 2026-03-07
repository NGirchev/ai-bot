# Модуль управления приоритетом пользователей (Bulkhead)

Этот модуль реализует паттерн **Bulkhead** для управления приоритетом пользователей при доступе к ресурсам системы, в частности, к ИИ-моделям.

## Функциональность

1. **Приоритеты пользователей**:
   - **ADMIN** → максимальный доступ.
   - **VIP** (оплатившие) → приоритетные ресурсы.
   - **Обычные** (бесплатные) → ограниченные ресурсы.
   - **Заблокированные** (не оплатили) → доступ запрещен.

2. **Пул потоков (Bulkhead) для запросов к ИИ-моделям**:
   - **ADMIN** → выделенный пул (20 потоков).
   - **VIP** → выделенный пул (10 потоков).
   - **Обычные пользователи** → общий пул (5 потоков).
   - **Заблокированные** → отказ сразу.

3. **Логирование**:
   - Логирование всех запросов (`INFO`).
   - Ошибки (`ERROR`), если пул исчерпан.

## Структура модуля

- `model/UserPriority.java` - перечисление приоритетов пользователей.
- `service/UserPriorityService.java` - интерфейс для проверки приоритета пользователя.
- `service/impl/UserPriorityServiceImpl.java` - реализация интерфейса, определяющая, ADMIN/VIP ли пользователь.
- `service/PriorityRequestExecutor.java` - обработка запросов с Bulkhead, выполняя их с учетом приоритета пользователя.
- `exception/AccessDeniedException.java` - исключение, выбрасываемое при отказе в доступе.
- `config/BulkHeadProperties.java` - класс для конфигурации параметров Bulkhead.

## Настройки

Настройки Bulkhead находятся в `application.yml`. Ключи в секции `instances` соответствуют значениям перечисления `UserPriority`:

```yaml
ai-bot:
  common:
    bulkhead:
      enabled: true
      instances:
        ADMIN:
          maxConcurrentCalls: 20
          maxWaitDuration: 1s
        VIP:
          maxConcurrentCalls: 10
          maxWaitDuration: 1s
        REGULAR:
          maxConcurrentCalls: 5
          maxWaitDuration: 500ms
        BLOCKED:
          maxConcurrentCalls: 0
          maxWaitDuration: 0ms
```

## Использование

### Синхронное выполнение запроса

```java
@Autowired
private PriorityRequestExecutor requestExecutor;

public String processRequest(Long userId, String prompt) {
    try {
        return requestExecutor.executeRequest(userId, () -> {
            // Здесь код для выполнения запроса
            return "Результат запроса";
        });
    } catch (AccessDeniedException e) {
        return "Доступ запрещен";
    } catch (Exception e) {
        return "Произошла ошибка";
    }
}
```

### Асинхронное выполнение запроса

```java
public CompletableFuture<String> processRequestAsync(Long userId, String prompt) {
    return requestExecutor.executeRequestAsync(userId, () -> {
        // Здесь код для выполнения запроса
        return "Результат запроса";
    }).toCompletableFuture()
      .exceptionally(e -> {
          if (e.getCause() instanceof AccessDeniedException) {
              return "Доступ запрещен";
          } else {
              return "Произошла ошибка";
          }
      });
}
```

## Расширение

Для использования модуля в вашем проекте:

1. Внедрите `PriorityRequestExecutor` в ваш сервис.
2. Реализуйте собственную логику определения приоритета пользователя в `UserPriorityServiceImpl`.
3. Используйте методы `executeRequest` и `executeRequestAsync` для выполнения запросов с учетом приоритета пользователя.

## Зависимости

- `resilience4j-spring-boot2`
- `resilience4j-bulkhead`
- `slf4j-api` 