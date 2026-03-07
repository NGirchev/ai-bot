# Логика ретраев и ротации моделей OpenRouter

## Обзор

Система автоматической ротации и ретраев для OpenRouter free-моделей реализована через AOP аспект `OpenRouterModelRotationAspect`, который перехватывает вызовы методов, помеченных аннотацией `@RotateOpenRouterModels`.

## Компоненты

### 1. `@RotateOpenRouterModels`
Аннотация для маркировки методов, которые должны использовать автоматическую ротацию моделей:
- `stream()` - флаг для стриминговых запросов (по умолчанию `false`)

**Использование:**
```java
@RotateOpenRouterModels
public AIResponse callChat(...) { ... }

@RotateOpenRouterModels(stream = true)
public AIResponse streamChat(...) { ... }
```

### 2. `OpenRouterModelRotationAspect`
AOP аспект, реализующий логику ротации и ретраев.

## Условия активации ротации

Ротация активируется только в следующих случаях:

1. **Free-модели OpenRouter** - если имя модели содержит `:free`
2. **AUTO-модели с max_price=0** - если:
   - Модель имеет тип `OPENAI` и capabilities содержат `ModelType.AUTO`
   - В `body` установлен `max_price=0` (или `options.max_price=0`)

Во всех остальных случаях аспект пропускает вызов без изменений.

## Процесс ротации

### 1. Определение кандидатов

Кандидаты определяются через `OpenRouterFreeModelResolver.candidatesForModel()`:

- Для `openrouter/auto` - возвращается ранжированный список всех free-моделей
- Для конкретной free-модели (например, `meta-llama/llama-3.2-3b-instruct:free`) - возвращается список с приоритетом на запрошенную модель
- Для обычных моделей - возвращается единственный кандидат (сама модель)

**Ранжирование моделей:**
1. **Score** (основной критерий):
   - Базовый score: 100.0
   - Штраф за latency: `-ewmaLatencyMs / 200.0`
   - Штрафы за ошибки:
     - 429 (rate limit): -30.0
     - 4xx (клиентские ошибки): -120.0
     - 5xx (серверные ошибки): -50.0
   - Cooldown: если модель в cooldown, score = -10_000.0 (исключается)

2. **Дополнительные критерии** (при равном score):
   - Предпочтение моделям, которые уже успешно работали (lastStatus == 200)
   - Предпочтение моделям с поддержкой tools (toolsSupported)
   - Лексикографический порядок имени модели

3. **Ограничение количества:**
   - Максимальное количество кандидатов ограничено `maxAttempts` из конфигурации
   - По умолчанию: 1 (если ранжирование выключено) или значение из `ai-bot.ai.spring-ai.openrouter-auto-rotation.max-attempts`

### 2. Синхронные запросы (`callWithRetry`)

```java
for (String modelId : candidates) {
    try {
        // Заменяем модель в аргументах
        Object[] args = replaceModelConfig(baseArgs, modelConfig, modelId);
        Object result = pjp.proceed(args);
        
        // Записываем успех
        recordSuccessIfPossible(modelId, latencyMs);
        return result;
    } catch (Exception e) {
        // Записываем неудачу
        recordFailureIfPossible(modelId, e, latencyMs);
        
        if (!isRetryable(e)) {
            throw e; // Неретраируемая ошибка - прерываем цикл
        }
        
        // Пробуем следующую модель
        log.warn("Retrying with next model...");
    }
}
```

### 3. Стриминговые запросы (`streamAttempt`)

Использует реактивный подход с `Flux.onErrorResume()`:

```java
Flux.defer(() -> {
    // Пробуем текущую модель
    return pjp.proceed(args).chatResponse();
})
.onErrorResume(error -> {
    if (!isRetryable(error) || index + 1 >= candidates.size()) {
        return Flux.error(error); // Прерываем ретраи
    }
    // Рекурсивно пробуем следующую модель
    return streamAttempt(pjp, baseArgs, modelConfig, candidates, index + 1);
});
```

## Retryable ошибки

Ошибка считается ретраируемой, если:

1. **HTTP статусы:**
   - `429` (Too Many Requests) - rate limiting
   - `500-599` (Server Errors) - временные проблемы сервера
   - `400` (Bad Request) - только если тело ответа содержит `"Conversation roles must alternate"` (специфичная валидация некоторых free-провайдеров)

2. **Транспортные ошибки:**
   - Timeouts
   - Network errors
   - Все остальные исключения (кроме `WebClientResponseException`)

**Неретраируемые ошибки:**
- `400` без специфичного сообщения (обычно означает несовместимость формата запроса)
- `401`, `403` (проблемы авторизации)
- Другие `4xx` ошибки

## Метрики и ранжирование

### Запись успеха (`recordSuccess`)
- Обновляет `lastStatus = 200`
- Обновляет EWMA latency: `ewma = current * (1 - alpha) + sample * alpha`
- Сбрасывает cooldown: `cooldownUntilEpochMs = 0`

### Запись неудачи (`recordFailure`)
- Обновляет `lastStatus` (HTTP статус ошибки)
- Обновляет EWMA latency
- Устанавливает cooldown для определенных ошибок:
  - `429`: cooldown из `ranking.cooldown-429` (по умолчанию из конфигурации)
  - `5xx`: cooldown из `ranking.cooldown-5xx`

**Cooldown** - период времени, в течение которого модель исключается из кандидатов (score = -10_000).

## Конфигурация

### Свойства Spring AI

```yaml
ai-bot:
  ai:
    spring-ai:
      openrouter-auto-rotation:
        max-attempts: 3  # Максимальное количество попыток ретрая
```

### Свойства OpenRouter Free Models

```yaml
ai-bot:
  common:
    openrouter-free-models:
      enabled: true
      ranking:
        enabled: true
        retry-max-attempts: 3
        latency-ewma-alpha: 0.2
        cooldown-429: PT5M      # 5 минут для rate limit
        cooldown-5xx: PT10M    # 10 минут для серверных ошибок
      filters:
        include-model-ids: []   # Белый список моделей
        exclude-model-ids: []   # Черный список моделей
        include-contains: []    # Модели, содержащие строку
        exclude-contains: []    # Исключить модели, содержащие строку
```

## Примеры использования

### Пример 1: Простой запрос с AUTO-моделью

```java
// В body: { "max_price": 0 }
// Модель: openrouter/auto
// Результат: автоматический выбор лучшей free-модели с ретраями
```

### Пример 2: Запрос к конкретной free-модели

```java
// Модель: meta-llama/llama-3.2-3b-instruct:free
// Результат: сначала пробуется запрошенная модель, затем другие free-модели при ошибках
```

### Пример 3: Стриминговый запрос

```java
@RotateOpenRouterModels(stream = true)
public AIResponse streamChat(...) {
    // При ошибке стрима автоматически переключается на следующую модель
    // без прерывания стрима для пользователя
}
```

## Логирование

Аспект логирует:
- Список кандидатов для ротации: `OpenRouter auto model rotation candidates (maxAttempts={}): {}`
- Предупреждения о ретраях: `Spring AI call failed for model={}, retrying next if available`
- Обновления статистики моделей: `OpenRouter model stats updated (success/failure)`
- Ранжирование кандидатов: `OpenRouter free model ranking: selected candidates`

## Важные замечания

1. **Производительность:** Ретраи увеличивают latency запроса. Используйте `maxAttempts` разумно (обычно 2-3).

2. **Cooldown:** Модели в cooldown автоматически исключаются из кандидатов, что предотвращает бесконечные ретраи к недоступным моделям.

3. **EWMA latency:** Используется для ранжирования моделей по скорости ответа. Модели с меньшей latency получают приоритет.

4. **Специфичные ошибки:** Некоторые free-провайдеры имеют строгую валидацию формата сообщений. Ошибка `400` с сообщением о ролях считается ретраируемой, так как может быть модель-специфичной.

5. **AUTO vs конкретная модель:** AUTO-ротация включается только при `max_price=0`, что явно указывает на желание использовать free-пул. В остальных случаях AUTO может означать другой механизм выбора модели.

## Связанные компоненты

- `OpenRouterFreeModelResolver` - резолвер и ранжирование моделей
- `OpenRouterStreamMetricsTracker` - метрики для стриминговых запросов
- `SpringAIChatService` - сервис, использующий аннотацию `@RotateOpenRouterModels`
- `SpringAIProperties` - конфигурация Spring AI модуля
