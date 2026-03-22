# Local Testing Guide for the Setup Wizard

## Prerequisites

- Node.js 18+ (`node --version`)
- Docker Desktop running (`docker info`)
- Real or fake Telegram bot token (format: `1234567890:ABCdef...`)
- Real or fake OpenRouter API key (format: `sk-or-v1-...`)

---

## Setup (once)

```bash
cd cli
npm install
```

---

## Test Cases

### Test 1 — OpenRouter path (happy path)

```bash
mkdir /tmp/test-openrouter && cd /tmp/test-openrouter
node /path/to/cli/bin/setup.js
```

Wizard input:
| Prompt | Value |
|--------|-------|
| Bot token | `1234567890:ABCdefGHIjklMNOpqrsTUVwxyz` (fake) |
| Bot username | `test_bot` |
| Admin Telegram ID | `123456789` |
| AI provider | OpenRouter |
| OpenRouter key | `sk-or-v1-fake` |
| Serper? | No |
| Optional services | leave Monitoring checked, uncheck rest |
| DB password | *(blank — auto-generate)* |
| Pull images? | No |

**Expected files created:**
```
/tmp/test-openrouter/
├── .env
├── docker-compose.yml
├── application-local.yml
├── prometheus.yml
└── application-local.yml.example
```

**Check `.env`:**
```bash
cat /tmp/test-openrouter/.env
```
- `TELEGRAM_TOKEN=1234567890:ABCdefGHIjklMNOpqrsTUVwxyz`
- `OPENROUTER_KEY=sk-or-v1-fake`
- `OLLAMA_BASE_URL=http://localhost:11434`
- `TELEGRAM_ACCESS_ADMIN_IDS=123456789`
- `COMPOSE_PROFILES=monitoring`
- `POSTGRES_PASSWORD=` — 32-char hex (auto-generated)

**Check `application-local.yml`:**
```bash
cat /tmp/test-openrouter/application-local.yml
```
- Должен быть закомментированный шаблон (OpenRouter path = minimal file)

**Check `docker-compose.yml`:**
```bash
grep -E "profiles:|SPRING_CONFIG|application-local" /tmp/test-openrouter/docker-compose.yml
```
- `profiles: ["monitoring"]` у prometheus/grafana
- `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/config/application-local.yml`
- `./application-local.yml:/app/config/application-local.yml:ro`

---

### Test 2 — Ollama path

```bash
mkdir /tmp/test-ollama && cd /tmp/test-ollama
node /path/to/cli/bin/setup.js
```

Wizard input:
| Prompt | Value |
|--------|-------|
| Bot token | `1234567890:ABCdefGHIjklMNOpqrsTUVwxyz` |
| Bot username | `test_bot` |
| Admin Telegram ID | `123456789` |
| AI provider | **Ollama** |
| Ollama URL | *(Enter, принять default `http://localhost:11434`)* |
| Serper? | No |
| Services | снять все галочки |
| DB password | `mypassword` |
| Pull images? | No |

**Проверки:**
```bash
# OPENROUTER_KEY должен быть пустым
grep OPENROUTER_KEY /tmp/test-ollama/.env

# OLLAMA_BASE_URL должен быть выставлен
grep OLLAMA_BASE_URL /tmp/test-ollama/.env

# application-local.yml должен содержать модели Ollama
cat /tmp/test-ollama/application-local.yml
```

- `COMPOSE_PROFILES=` (пустая строка — ни один профиль не активен)
- `prometheus.yml` НЕ должен быть создан (monitoring не выбран)
- `application-local.yml` содержит `provider-type: OLLAMA`, `qwen2.5:7b`, `nomic-embed-text:v1.5`
- `spring.ai.ollama.base-url: ${OLLAMA_BASE_URL:http://localhost:11434}`

---

### Test 3 — Overwrite existing `.env`

```bash
cd /tmp/test-openrouter   # директория из теста 1
node /path/to/cli/bin/setup.js
```

- Wizard должен спросить `.env already exists. Overwrite it?`
- Ответить **No** → должен напечатать `Aborted.` и выйти без изменений
- Запустить снова, ответить **Yes** → wizard продолжается

---

### Test 4 — Отмена по Ctrl+C

```bash
mkdir /tmp/test-cancel && cd /tmp/test-cancel
node /path/to/cli/bin/setup.js
```

Нажать `Ctrl+C` на любом вопросе.

- Должен напечатать `Setup cancelled.`
- Не должен падать со stack trace
- Никакие файлы не должны быть созданы (или только те, что до отмены)

---

### Test 5 — Все сервисы включены

```bash
mkdir /tmp/test-all-services && cd /tmp/test-all-services
node /path/to/cli/bin/setup.js
```

На шаге `Which optional services to start?` выбрать все три (Space на каждом).

**Проверка:**
```bash
grep COMPOSE_PROFILES /tmp/test-all-services/.env
```
Ожидается: `COMPOSE_PROFILES=monitoring,logging,storage`

---

### Test 6 — Валидация полей

```bash
mkdir /tmp/test-validation && cd /tmp/test-validation
node /path/to/cli/bin/setup.js
```

- **Bot token**: ввести пробел → должен показать `Required`, не продолжать
- **Admin Telegram ID**: ввести `abc123` → должен показать `Must be a number`
- **Admin Telegram ID**: ввести `123456789` → проходит

---

### Test 7 — npm pack (симуляция npx)

Проверяет, что в пакет включены все нужные файлы.

```bash
cd cli
npm pack --dry-run
```

Вывод должен содержать:
```
bin/setup.js
templates/docker-compose.yml
templates/prometheus.yml
templates/application-local.yml.example
package.json
README.md
```

Полная симуляция с упаковкой:
```bash
npm pack
# Создаёт: ngirchev-open-daimon-1.0.0.tgz

mkdir /tmp/test-pack && cd /tmp/test-pack
node /absolute/path/to/cli/ngirchev-open-daimon-1.0.0.tgz/bin/setup.js
# или:
npx /absolute/path/to/cli/ngirchev-open-daimon-1.0.0.tgz
```

---

## Важное ограничение при тестировании

Шаблон `cli/templates/docker-compose.yml` использует:
```yaml
image: ghcr.io/ngirchev/open-daimon:latest
```

До публикации Docker образа `docker compose up -d` завершится ошибкой pull. Чтобы проверить что стек реально поднимается — используй **корневой** `docker-compose.yml` (который собирает образ из исходников):

```bash
# В корне репозитория:
cp /tmp/test-openrouter/.env .
cp /tmp/test-openrouter/application-local.yml .
docker compose up -d --build
docker compose logs -f opendaimon-app
```

---

## Чеклист перед публикацией в npm

- [ ] Test 1 пройден: все файлы создаются, содержимое корректное
- [ ] Test 2 пройден: Ollama конфиг в `application-local.yml` правильный
- [ ] Test 3 пройден: overwrite работает
- [ ] Test 4 пройден: Ctrl+C не даёт stack trace
- [ ] Test 5 пройден: `COMPOSE_PROFILES` содержит все три профиля
- [ ] Test 6 пройден: валидация работает
- [ ] Test 7 пройден: `npm pack --dry-run` показывает все нужные файлы
- [ ] Стек поднимается через корневой `docker-compose.yml` с `.env` от визарда
- [ ] `application-local.yml` монтируется и подхватывается Spring Boot (`docker compose logs` не показывает ошибок конфига)
- [ ] Опубликован Docker образ на GHCR (`publish-docker.yml` workflow)
