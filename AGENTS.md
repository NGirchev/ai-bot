# AGENTS.md

## Правила для AI-агентов

### При создании новых сервисов и компонентов
1. **НЕ используй `@Service`, `@Component`, `@Repository`** для автоматического сканирования бинов
2. **Создавай бины явно** в конфигурационных классах через `@Bean` методы
3. **Конфигурационные классы** находятся в пакете `config` каждого модуля
4. **Пример структуры**:
   ```java
   // ❌ НЕПРАВИЛЬНО:
   @Service
   public class MyService { ... }
   
   // ✅ ПРАВИЛЬНО:
   public class MyService { ... }  // Без аннотаций
   
   @Configuration
   public class MyModuleConfig {
       @Bean
       @ConditionalOnMissingBean
       public MyService myService(...) {
           return new MyService(...);
       }
   }
   ```
5. **Исключения**: `@Repository` на интерфейсах JPA репозиториев допустимо (это интерфейсы, не классы)

### При создании новых модулей
1. **Создай pom.xml** с правильной структурой зависимостей (см. Code Style)
2. **Добавь модуль в parent pom.xml** в секцию `<modules>`
3. **Создай package structure**: `ru.girchev.aibot.<module-name>.<layer>`
4. **Если нужны Entity**: наследуй от `User` или `Message` из `aibot-common`
5. **Создай Flyway миграцию** в `aibot-app/src/main/resources/db/migration/`
6. **Создай конфигурационный класс** для всех бинов модуля (e.g., `MyModuleConfig`)

### При работе с Entity
1. **НЕ дублируй Entity** между модулями - используй наследование
2. **Базовые Entity** только в `aibot-common`
3. **Специфичные поля** в дочерних Entity (e.g., `telegram_id` в `TelegramUser`)
4. **Используй JPA Inheritance JOINED** для User
5. **Используй JPA Inheritance SINGLE_TABLE** для Message (все сообщения в одной таблице, специфичные данные в metadata JSONB)
6. **Discriminator** обязателен для полиморфных запросов

### При добавлении новых AI-провайдеров
1. **Создай новый модуль** `ai-<provider-name>` (e.g., `ai-anthropic`)
2. **Создай Service** с методом `generateResponse(String prompt, ...)`
3. **Создай Properties** для конфигурации (API key, URL)
4. **Добавь зависимость** в модули, которые будут использовать провайдера
5. **НЕ добавляй Entity** - провайдеры stateless

### При работе с БД
1. **Все миграции** в `aibot-app/src/main/resources/db/migration/`
2. **Формат**: `V<number>__<description>.sql` (e.g., `V1__Create_initial_tables.sql`)
3. **Индексы обязательны** для foreign keys и часто используемых полей
4. **Используй `IF NOT EXISTS`** для идемпотентности
5. **Timestamps**: `TIMESTAMP WITH TIME ZONE` (не `TIMESTAMP`)

### При добавлении метрик
1. **Используй `AiBotMeterRegistry`** из `aibot-common`
2. **Формат метрик**: `<module>.<action>.<metric>` (e.g., `rest.request.processing.time`)
3. **Типы метрик**: Counter, Timer, Gauge
4. **Добавь описание** в Grafana dashboard

### При работе с приоритизацией
1. **Используй `PriorityRequestExecutor`** для всех AI-запросов
2. **НЕ вызывай AI-сервисы напрямую** - только через executor
3. **Приоритеты**: ADMIN (10 потоков), VIP (5 потоков), REGULAR (1 поток)
4. **Whitelist** управляется через `WhitelistService`

### Безопасность
1. **API keys** ТОЛЬКО в environment variables
2. **НЕ коммить** `application.yml` с реальными ключами
3. **Используй `@PreAuthorize`** для защиты REST endpoints (если добавишь Spring Security)
4. **Валидация входных данных** через Jakarta Validation (`@Valid`, `@NotNull`, etc.)

### Тестирование
1. **Unit tests** для сервисов (Mockito)
2. **Integration tests** для репозиториев (Testcontainers)
3. **Покрытие** минимум 70% для критичной бизнес-логики
4. **Не мокай Entity** - используй реальные объекты
5. **Используй `@DataJpaTest`** для тестов репозиториев

## Project Overview

**AI Bot Router** - многомодульный Java-проект для взаимодействия с различными AI-сервисами через разные интерфейсы (Telegram, REST API, Web UI) с интеграцией через Spring AI (OpenRouter, Ollama) и возможностью добавления собственного контекста и шаблонов.

### Архитектурная концепция

Проект построен по принципу **модульной архитектуры**, где каждый модуль может быть собран независимо под конкретного клиента. Например:
- Можно собрать только `aibot-telegram` без `aibot-rest`
- Можно подключить только `aibot-rest` без `aibot-telegram`, или `aibot-ui` без `aibot-telegram`
- Можно собрать только `aibot-spring-ai` без интерфейсных модулей (подключив нужные точки входа)
- Можно включать `aibot-gateway-mock` как провайдер-заглушку для тестовых сценариев без внешних API
- Каждый модуль имеет свои Entity и может работать автономно

### Технологический стек

- **Java 21** (LTS)
- **Spring Boot 3.3.3** (Spring Framework 6.2.6)
- **Maven** (multi-module project)
- **PostgreSQL 17.0** с Flyway миграциями
- **Resilience4j** для bulkhead pattern (приоритизация запросов)
- **Caffeine** для кэширования
- **Micrometer + Prometheus + Grafana** для метрик
- **Elasticsearch + Kibana + Metricbeat** для логирования
- **Testcontainers** для интеграционных тестов
- **Lombok** для уменьшения boilerplate кода

## Модульная структура

### 1. `aibot-common` (Core Module)
**Назначение**: Базовый модуль с общей бизнес-логикой, моделями и сервисами.

**Ключевые компоненты**:
- `User` - базовая Entity с JPA Inheritance (JOINED strategy)
- `Message` - Entity для хранения сообщений в диалоге (объединяет функциональность UserRequest и ServiceResponse)
- `CommandHandler<T, C, R>` - интерфейс для обработки команд (паттерн Command)
- `PriorityRequestExecutor` - сервис для приоритизации запросов (ADMIN/VIP/REGULAR)
- `BulkHeadAutoConfig`, `BulkHeadProperties` - конфигурация пулов потоков
- `AIBotMeterRegistry` - метрики для мониторинга
- `OpenRouterFreeModelsAutoConfig`, `OpenRouterFreeModelsProperties` - работа с бесплатными моделями OpenRouter

**Зависимости**: Spring Data JPA, PostgreSQL, Resilience4j, Caffeine, Micrometer

### 2. `aibot-telegram` (Telegram Interface Module)
**Назначение**: Модуль для работы с Telegram Bot API.

**Ключевые компоненты**:
- `TelegramBot`
- Конфигурация: `TelegramAutoConfig`, `TelegramServiceConfig`, `TelegramProperties`
- Command handlers: `StartTelegramCommandHandler`, `MessageTelegramCommandHandler`, `RoleTelegramCommandHandler`, `NewThreadTelegramCommandHandler`, `HistoryTelegramCommandHandler`, `ThreadsTelegramCommandHandler`, `BugreportTelegramCommandHandler`
- Сервисы: `TelegramUserService`, `TelegramMessageService`, `TelegramUserSessionService`, `TelegramWhitelistService`, `TypingIndicatorService`
- Entities: `TelegramUser`, `TelegramUserSession`, `TelegramWhitelist`

**Зависимости**: `aibot-common`, Telegram Bots API (6.9.7.0)

**Таблицы БД**: `telegram_user`, `telegram_user_session`, `telegram_whitelist`

### 3. `aibot-rest` (REST API Module)
**Назначение**: Модуль для предоставления REST API.

**Ключевые компоненты**:
- `RestUser extends User` - Entity для REST-пользователей (поле `email`)
- Контроллеры: `SessionController`
- Handlers: `RestChatMessageCommandHandler`, `RestChatStreamMessageCommandHandler`
- Конфигурация: `RestAutoConfig`, `RestFlywayConfig`, `RestJpaConfig`
- Сервисы: `ChatService`, `RestUserService`, `RestMessageService`, `RestAuthorizationService`
- Исключения: `RestExceptionHandler`, `UnauthorizedException`

**Зависимости**: `aibot-common`, Springdoc OpenAPI (Swagger)

**Таблицы БД**: `rest_user`

### 4. `aibot-ui` (Web UI Module)
**Назначение**: Веб-интерфейс для работы через браузер.

**Ключевые компоненты**:
- `PageController`, `UIAuthController`
- `UIAutoConfig`, `UIProperties`
- Шаблоны: `templates/login.html`, `templates/chat.html`
- Статика: `static/css/chat.css`, `static/js/chat.js`

**Зависимости**: `aibot-rest`, Spring Boot Web, Thymeleaf

### 5. `aibot-spring-ai` (Spring AI Integration Module)
**Назначение**: Интеграция с LLM провайдерами через Spring AI (OpenAI/OpenRouter, Ollama) и чат-память.

**Ключевые компоненты**:
- Конфигурация: `SpringAIAutoConfig`, `SpringAIProperties`, `SpringAIModelConfig`, `SpringAIFlywayConfig`
- Сервисы: `SpringAIGateway`, `SpringAIChatService`, `SpringAIPromptFactory`
- Chat memory: `SummarizingChatMemory`
- Ротация моделей: `OpenRouterModelRotationAspect`, `RotateOpenRouterModels`
- Web/логирование: `RestClientLogCustomizer`, `WebClientLogCustomizer`
- Tools: `WebTools`

**Зависимости**: `aibot-common`, Spring AI, WebClient

### 6. `aibot-gateway-mock` (Gateway Mock Module)
**Назначение**: Заглушка для интеграционных тестов и сценариев без внешнего API.

**Ключевые компоненты**:
- Моки ответов провайдера
- DTO для тестовых сценариев

**Зависимости**: `aibot-common`

### 7. `aibot-app` (Application Module)
**Назначение**: Основной модуль для запуска приложения, объединяет все модули.

**Ключевые компоненты**:
- `Application` - главный класс с `@SpringBootApplication`
- Flyway миграции в `src/main/resources/db/migration/`
- `application.yml` - конфигурация приложения

**Зависимости**: `aibot-telegram`, `aibot-rest`, `aibot-ui`, `aibot-spring-ai`, `aibot-gateway-mock` (транзитивно подтягивает все остальные модули)

## Структура базы данных

### Иерархия наследования (JPA Inheritance)
```
user (базовая таблица, JOINED strategy)
├── telegram_user (telegram_id)
└── rest_user (email)

message (базовая таблица, SINGLE_TABLE strategy)
- Хранит все сообщения (USER, ASSISTANT, SYSTEM)
- Telegram-специфичные данные хранятся в metadata (session_id)
- REST-специфичные данные хранятся в metadata (client_ip, user_agent, endpoint)
```

### Основные таблицы
- `user` - базовая таблица пользователей (discriminator: `user_type`)
- `telegram_user`, `rest_user` - специфичные таблицы для разных типов пользователей
- `message` - все сообщения в диалогах (объединяет функциональность user_request и service_response)
- `telegram_user_session` - сессии Telegram-пользователей
- `telegram_whitelist` - whitelist для доступа к боту
- `conversation_thread` - потоки диалогов для группировки сообщений

## Code Style и конвенции

### Структура зависимостей в pom.xml
**ВАЖНО**: Следуй этому порядку в КАЖДОМ pom.xml (см. комментарии в файлах):
1. Project-specific modules (groupId: `ru.girchev`)
2. Spring dependencies (groupId: `org.springframework`)
3. Database dependencies (jdbc, jpa, postgres, h2)
4. Other utilities and libraries (logging, json, etc.)
5. Test-related dependencies (scope: `test`)

**Все версии ДОЛЖНЫ быть вынесены в `<properties>`!**

### Java Code Style
- **Java 21** с использованием современных фич
- **Lombok** для уменьшения boilerplate (`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`)
- **Functional patterns** где возможно (используется библиотека Vavr)
- **Package structure**: `ru.girchev.aibot.<module>.<layer>` (e.g., `ru.girchev.aibot.telegram.service`)

### Entity Guidelines
- Базовые Entity в `aibot-common` (`User`, `Message`)
- Специфичные Entity в модулях (`TelegramUser`, `RestUser`)
- Используется **JPA Inheritance JOINED** для User
- Используется **JPA Inheritance SINGLE_TABLE** для Message (все сообщения в одной таблице)
- `@PrePersist` и `@PreUpdate` для автоматического заполнения timestamps
- Discriminator column: `user_type` (values: `TELEGRAM`, `REST`) для User
- Discriminator column: `message_type` для Message (по умолчанию `MESSAGE`)

### Service Layer
- Интерфейсы для сервисов (e.g., `UserService`, `UserPriorityService`)
- Реализации с суффиксом `Impl` (e.g., `UserPriorityServiceImpl`)
- `@RequiredArgsConstructor` для dependency injection
- `@Slf4j` для логирования

### Spring Bean Configuration
**ВАЖНО**: В этом проекте НЕ используются аннотации `@Service`, `@Component`, `@Repository` для автоматического сканирования бинов!
- **Все бины создаются явно** в конфигурационных классах через `@Bean` методы
- **Конфигурационные классы** находятся в пакете `config` каждого модуля (e.g., `TelegramServiceConfig`, `CoreAutoConfig`)
- **Преимущества**: явный контроль над созданием бинов, условная конфигурация через `@ConditionalOnProperty`, лучшая тестируемость
- **Пример**: вместо `@Service` на классе создай `@Bean` метод в соответствующем `*Config` классе

#### Пример использования ObjectProvider:

```java
// ✅ ПРАВИЛЬНО: использование ObjectProvider для опциональных/ленивых бинов
@Bean
@ConditionalOnMissingBean
public MessageTelegramCommandHandler messageTelegramCommandHandler(
        ObjectProvider<TelegramBot> telegramBotProvider,  // Опциональный бин
        PriorityRequestExecutor priorityRequestExecutor,
        // ... другие зависимости
) {
    return new MessageTelegramCommandHandler(telegramBotProvider, priorityRequestExecutor, ...);
}

// В классе handler:
public class MessageTelegramCommandHandler {
    private final ObjectProvider<TelegramBot> telegramBotProvider;
    
    public void sendMessage(Long chatId, String text) {
        // Бин получается только при необходимости
        telegramBotProvider.getObject().sendMessage(chatId, text);
    }
}
```

**Когда использовать ObjectProvider:**
- Когда бин может отсутствовать (опциональный)
- Когда нужна ленивая загрузка (получение бина только при использовании)
- Когда нужно избежать циклических зависимостей
- Когда бин создается условно (через `@ConditionalOnProperty`)

**Когда использовать @Lazy:**
- Когда бин всегда должен существовать, но нужна ленивая инициализация
- Когда нужно разорвать циклическую зависимость на уровне создания бина

### Command Pattern
- Интерфейс `CommandHandler<T extends CommandType, C extends Command<T>, R>`
- Каждый модуль имеет свою реализацию (e.g., `TelegramCommandHandler`)
- Registry для регистрации обработчиков (`AiBotCommandHandlerRegistry`)

### Метрики и мониторинг
- Используй `AiBotMeterRegistry` для регистрации метрик
- Метрики должны быть в формате: `<module>.<action>.<metric>` (e.g., `telegram.message.processing.time`)
- Все метрики экспортируются в Prometheus

## Конфигурация

- Структура конфигурации соответствует `ai-bot.*` (модули `telegram`, `rest`, `ui`, `ai.spring-ai`), все feature toggles работают через `*.enabled`.
- Конфиги и комментарии по ключам хранятся в `aibot-app/src/main/resources/application.yml`.
