# Инструкция по деплою AI Bot на сервере

> **Для локальной разработки** см. [README.md](README.md)

Эта инструкция описывает процесс деплоя приложения на production сервер через Docker Compose.

## Краткая сводка: что происходит при деплое

1. **Volumes создаются автоматически** при первом запуске `docker-compose up -d`:
   - `postgres-data` - база данных (критично!)
   - `grafana-storage` - дашборды Grafana
   - `elasticsearch-data` - индексы Elasticsearch
   - `prometheus-data` - метрики Prometheus

2. **Данные сохраняются** между перезапусками и перезагрузками сервера

3. **Важно**: При удалении volumes (`docker-compose down -v`) все данные будут потеряны!

## Предварительные требования

1. **Java 21** (LTS)
2. **Maven 3.11+**
3. **Docker** и **Docker Compose**
4. **Git** (для клонирования репозитория)

## Шаг 1: Подготовка сервера

### Установка зависимостей

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-21-jdk maven docker.io docker-compose git

# Проверка версий
java -version  # должна быть 21
mvn -version
docker --version
docker-compose --version
```

### Настройка Docker (если нужно)

```bash
# Добавить пользователя в группу docker
sudo usermod -aG docker $USER
newgrp docker
```

## Шаг 2: Клонирование и сборка проекта

```bash
# Клонировать репозиторий
git clone <your-repo-url>
cd ai-bot

# Собрать проект
mvn clean package -DskipTests

# Проверить, что JAR создан
ls -lh aibot-app/target/aibot-app-1.0-SNAPSHOT.jar
```

## Шаг 3: Проверка конфигурационных файлов

Убедитесь, что следующие файлы настроены правильно:
- **[Dockerfile](Dockerfile)** - должен быть в корне проекта
- **[docker-compose.yml](docker-compose.yml)** - должен содержать сервис `aibot-app` с правильными переменными окружения

## Шаг 4: Создание .env файла

Создайте файл `.env` в корне проекта с переменными окружения:

```bash
# Telegram Bot
TELEGRAM_USERNAME=your_bot_username
TELEGRAM_TOKEN=your_telegram_bot_token

# Whitelist (опционально, через запятую)
WHITELIST_EXCEPTIONS=
WHITELIST_CHANNEL_ID_EXCEPTIONS=

# AI Providers
DEEPSEEK_KEY=your_deepseek_api_key
OPENROUTER_KEY=your_openrouter_api_key

# Database (если нужно изменить)
POSTGRES_DB=ai_bot
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password
```

> **Примечание**: `.env` уже добавлен в `.gitignore`, поэтому не будет закоммичен в репозиторий.

## Шаг 5: Проверка конфигурации

Убедитесь, что следующие файлы настроены правильно:
- **[prometheus.yml](prometheus.yml)** - должен содержать target `aibot-app:8080`
- **[aibot-app/src/main/resources/application.yml](aibot-app/src/main/resources/application.yml)** - должен использовать переменные окружения для подключения к БД

## Шаг 6: Выбор docker-compose файла

Для Ubuntu Server рекомендуется использовать `docker-compose.ubuntu.yml`, который:
- ✅ Использует дефолтную сеть Docker Compose (стандартный подход)
- ✅ Включает healthchecks для всех сервисов
- ✅ Оптимизирован для сервера (лимиты ресурсов)
- ✅ Содержит оптимизированные настройки PostgreSQL

**Использование на Ubuntu Server:**

**Вариант 1: Использовать скрипт (рекомендуется - автоматически исправляет iptables)**

```bash
# Сделать скрипт исполняемым
chmod +x docker-compose-up-ubuntu.sh

# Запустить (скрипт автоматически исправит iptables, если нужно)
./docker-compose-up-ubuntu.sh
```

**Вариант 2: Вручную (если скрипт не подходит)**

```bash
# 1. Исправить iptables (если возникает ошибка)
sudo iptables -t filter -N DOCKER-ISOLATION-STAGE-2
sudo systemctl restart docker

# 2. Запустить docker-compose
docker-compose -f docker-compose.ubuntu.yml up -d

# Проверка статуса
docker-compose -f docker-compose.ubuntu.yml ps
```

**Для локальной разработки (macOS/Windows):**

```bash
docker-compose up -d
```

> **Примечание**: Если возникают проблемы с iptables на Ubuntu Server, см. раздел "Проблема с iptables" ниже.

## Шаг 7: Запуск приложения

### Первый запуск

```bash
# Для Ubuntu Server
docker-compose -f docker-compose.ubuntu.yml up -d

# Или для локальной разработки
docker-compose up -d

# Проверить статус
docker-compose ps

# Просмотр логов приложения
docker-compose logs -f aibot-app

# Просмотр логов всех сервисов
docker-compose logs -f
```

### Volumes (хранение данных)

**Важно**: При первом запуске Docker автоматически создаст volumes для хранения данных:
- `postgres-data` - данные базы данных PostgreSQL
- `grafana-storage` - дашборды и настройки Grafana
- `elasticsearch-data` - индексы Elasticsearch
- `prometheus-data` - метрики Prometheus

**Проверка volumes:**
```bash
# Список всех volumes
docker volume ls

# Информация о конкретном volume
docker volume inspect ai-bot_postgres-data

# Где физически хранятся данные (обычно /var/lib/docker/volumes/)
docker volume inspect ai-bot_postgres-data | grep Mountpoint
```

**Важно для production:**
- ✅ Volumes создаются автоматически при первом запуске `docker-compose up -d`
- ✅ Данные сохраняются между перезапусками контейнеров
- ✅ Данные сохраняются после перезагрузки сервера
- ✅ Volumes не удаляются при `docker-compose down` (только при `docker-compose down -v`)
- ⚠️ При удалении volume все данные будут потеряны!

**Управление volumes:**
```bash
# Просмотр использования места
docker system df -v

# Backup volume (пример для postgres)
docker run --rm -v ai-bot_postgres-data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz /data

# Удаление volume (ОСТОРОЖНО! Удалит все данные!)
docker volume rm ai-bot_postgres-data
```

### Автоматический перезапуск

Все сервисы в [docker-compose.yml](docker-compose.yml) настроены с `restart: unless-stopped`, что означает:
- ✅ Контейнеры автоматически перезапустятся при падении
- ✅ Контейнеры автоматически запустятся после перезагрузки сервера (если Docker daemon запускается автоматически)

**Убедитесь, что Docker запускается автоматически:**
```bash
# Проверить статус
sudo systemctl status docker

# Включить автозапуск Docker (если не включен)
sudo systemctl enable docker
```

> **Примечание**: Для production рекомендуется также настроить systemd сервис (см. Шаг 9) для большей надежности и контроля.

### Проверка работы

```bash
# Проверить, что приложение запущено
curl http://localhost:8080/actuator/health

# Проверить метрики
curl http://localhost:8080/actuator/prometheus

# Проверить Swagger UI (если REST API включен)
# Откройте в браузере: http://your-server-ip:8080/swagger-ui/index.html
```

## Шаг 7: Настройка мониторинга

### Prometheus
- URL: `http://your-server-ip:9090`
- Проверьте targets: `http://your-server-ip:9090/targets`

### Grafana
- URL: `http://your-server-ip:3000`
- Логин: `admin`
- Пароль: `admin123456`
- Добавьте Prometheus как data source: `http://prometheus:9090`

### Kibana
- URL: `http://your-server-ip:5601`
- Настройте index pattern для логов

## Шаг 8: Обновление приложения

```bash
# Остановить приложение
docker-compose stop aibot-app

# Пересобрать JAR (если были изменения)
mvn clean package -DskipTests

# Пересобрать Docker образ
docker-compose build aibot-app

# Запустить заново
docker-compose up -d aibot-app

# Проверить логи
docker-compose logs -f aibot-app
```

## Шаг 9: Настройка автозапуска через systemd (опционально, но рекомендуется)

> **Примечание**: С `restart: unless-stopped` в docker-compose.yml и включенным автозапуском Docker, контейнеры будут автоматически запускаться после перезагрузки. Systemd сервис добавляет дополнительный уровень контроля и мониторинга.

Создайте файл `/etc/systemd/system/ai-bot.service`:

```ini
[Unit]
Description=AI Bot Application
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/path/to/ai-bot
ExecStart=/usr/bin/docker-compose up -d
ExecStop=/usr/bin/docker-compose down
User=your-user

[Install]
WantedBy=multi-user.target
```

Активируйте сервис:

```bash
sudo systemctl daemon-reload
sudo systemctl enable ai-bot.service
sudo systemctl start ai-bot.service
```

## Полезные команды

```bash
# Просмотр логов
docker-compose logs -f aibot-app
docker-compose logs -f postgres

# Перезапуск конкретного сервиса
docker-compose restart aibot-app

# Остановка всех сервисов
docker-compose down

# Остановка с удалением volumes (ОСТОРОЖНО!)
docker-compose down -v

# Просмотр использования ресурсов
docker stats

# Вход в контейнер
docker exec -it ai-bot-app sh
docker exec -it ai-bot-postgres psql -U postgres -d ai_bot
```

## Troubleshooting

### Приложение не запускается

```bash
# Проверьте логи
docker-compose logs aibot-app

# Проверьте, что БД доступна
docker-compose exec postgres psql -U postgres -d ai_bot -c "SELECT 1;"

# Проверьте переменные окружения
docker-compose exec aibot-app env | grep -E "TELEGRAM|DEEPSEEK|OPENROUTER"
```

### Проблемы с подключением к БД

```bash
# Проверьте, что postgres запущен
docker-compose ps postgres

# Проверьте сеть
docker network inspect ai-bot_ai-bot-network

# Проверьте логи postgres
docker-compose logs postgres
```

### Проблемы с метриками в Prometheus

```bash
# Проверьте targets в Prometheus UI
# Убедитесь, что aibot-app доступен по имени в сети

# Проверьте конфигурацию prometheus.yml
docker-compose exec prometheus cat /etc/prometheus/prometheus.yml
```

### Проблема с iptables на Linux сервере (ошибка DOCKER-ISOLATION-STAGE-2)

Если при запуске `docker-compose up -d` возникает ошибка:
```
failed to create network ai-bot_ai-bot-network: Error response from daemon: 
add inter-network communication rule: (iptables failed: iptables --wait -t filter 
-A DOCKER-ISOLATION-STAGE-1 -i br-xxx ! -o br-xxx -j DOCKER-ISOLATION-STAGE-2: 
iptables v1.8.10 (nf_tables): Chain 'DOCKER-ISOLATION-STAGE-2' does not exist
```

**Решение 1: Перезапустить Docker daemon (рекомендуется)**

```bash
# Перезапустить Docker
sudo systemctl restart docker

# Попробовать запустить снова
docker-compose up -d
```

**Решение 2: Настроить Docker для использования iptables (официальный подход Docker, рекомендуется)**

Согласно [официальной документации Docker](https://docs.docker.com/engine/network/packet-filtering-firewalls/), можно явно указать Docker использовать `iptables` вместо `nftables` через параметр `firewall-backend`:

```bash
# Создать или отредактировать /etc/docker/daemon.json
sudo nano /etc/docker/daemon.json
```

Добавьте или обновите конфигурацию:
```json
{
  "firewall-backend": "iptables"
}
```

Затем перезапустите Docker:
```bash
sudo systemctl restart docker

# Попробовать запустить снова
docker-compose up -d
```

> **Примечание**: Это официальный способ указать Docker использовать iptables. Docker поддерживает оба бэкенда (iptables и nftables), но iptables более стабилен на Ubuntu Server.

**Решение 3: Переключиться на iptables-legacy (альтернатива, если решение 2 не помогло)**

Если настройка `firewall-backend` не помогла, можно переключиться на `iptables-legacy` на уровне системы:

```bash
# Переключиться на iptables-legacy
sudo update-alternatives --set iptables /usr/sbin/iptables-legacy
sudo update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy

# Перезапустить Docker
sudo systemctl restart docker

# Попробовать запустить снова
docker-compose up -d
```

**Решение 4: Исправить iptables вручную (если предыдущие решения не помогли)**

```bash
# Создать недостающую цепочку
sudo iptables -t filter -N DOCKER-ISOLATION-STAGE-2

# Перезапустить Docker
sudo systemctl restart docker

# Попробовать запустить снова
docker-compose up -d
```

**Решение 5: Использовать дефолтную сеть Docker Compose**

Если предыдущие решения не помогли, можно закомментировать определение сети в `docker-compose.yml`:

1. Откройте `docker-compose.yml`
2. Закомментируйте секцию `networks:` в конце файла:
   ```yaml
   # networks:
   #   ai-bot-network:
   #     driver: bridge
   ```
3. Удалите все `networks: - ai-bot-network` из всех сервисов
4. Docker Compose автоматически создаст сеть с именем проекта

**Решение 6: Настроить Docker для использования iptables (официальный подход Docker)**

Согласно [официальной документации Docker](https://docs.docker.com/engine/network/packet-filtering-firewalls/), можно явно указать Docker использовать `iptables` вместо `nftables`:

```bash
# Создать или отредактировать /etc/docker/daemon.json
sudo nano /etc/docker/daemon.json
```

Добавьте или обновите конфигурацию:
```json
{
  "firewall-backend": "iptables"
}
```

Затем перезапустите Docker:
```bash
sudo systemctl restart docker
```

> **Примечание**: Это официальный способ указать Docker использовать iptables. Docker поддерживает оба бэкенда (iptables и nftables), но iptables более стабилен на Ubuntu Server.

**Решение 7: Отключить управление iptables в Docker (не рекомендуется)**

Если предыдущие решения не помогли, можно отключить управление iptables в Docker:

```bash
# Создать или отредактировать /etc/docker/daemon.json
sudo nano /etc/docker/daemon.json
```

Добавьте:
```json
{
  "iptables": false
}
```

Затем перезапустите Docker:
```bash
sudo systemctl restart docker
```

> **Примечание**: Отключение iptables в Docker может потребовать ручной настройки firewall правил. Используйте только если другие решения не помогли.

## Безопасность

1. **Измените пароли по умолчанию** в `.env` файле
2. **Настройте firewall** на сервере (откройте только нужные порты)
3. **Используйте HTTPS** для внешнего доступа (через nginx reverse proxy)
4. **Регулярно обновляйте** Docker образы

## Масштабирование (несколько экземпляров приложения)

Для запуска нескольких экземпляров приложения:

1. **Уберите `container_name`** из сервиса `aibot-app` в [docker-compose.yml](docker-compose.yml):
   ```yaml
   aibot-app:
     # container_name: ai-bot-app  # Закомментируйте для масштабирования
     ports:
       - "8080:8080"  # Или используйте диапазон: "8080-8090:8080"
   ```

2. **Запустите с масштабированием**:
   ```bash
   # Запустить 3 экземпляра приложения
   docker-compose up -d --scale aibot-app=3
   
   # Проверить статус
   docker-compose ps
   
   # Просмотр логов всех экземпляров
   docker-compose logs -f aibot-app
   ```

4. **Используйте load balancer** (nginx, traefik) для распределения нагрузки между экземплярами

> **Примечание**: Для Telegram бота обычно достаточно одного экземпляра, так как Telegram сам управляет распределением сообщений. Масштабирование полезно для REST API или при высокой нагрузке.

## Production рекомендации

1. **Volumes для сохранения данных** - все сервисы с данными используют volumes:
   - ✅ **postgres** - `postgres-data` volume (данные БД сохраняются)
   - ✅ **grafana** - `grafana-storage` volume (дашборды и настройки сохраняются)
   - ✅ **elasticsearch** - `elasticsearch-data` volume (индексы сохраняются)
   - ✅ **prometheus** - `prometheus-data` volume (метрики сохраняются)
   
   > **Важно**: Все volumes создаются автоматически при первом запуске. Данные сохраняются между перезапусками контейнеров и перезагрузками сервера.

2. **Настройте регулярный backup БД**:
   ```bash
   # Backup БД (рекомендуется делать регулярно)
   docker-compose exec postgres pg_dump -U postgres ${POSTGRES_DB:-ai_bot} > backup-$(date +%Y%m%d-%H%M%S).sql
   
   # Restore БД
   docker-compose exec -T postgres psql -U postgres ${POSTGRES_DB:-ai_bot} < backup.sql
   
   # Автоматический backup через cron (добавьте в crontab)
   # 0 2 * * * cd /path/to/ai-bot && docker-compose exec -T postgres pg_dump -U postgres ai_bot > /backups/ai-bot-$(date +\%Y\%m\%d).sql
   ```

3. **Backup volumes (опционально, для полного резервного копирования)**:
   ```bash
   # Backup всех volumes
   docker run --rm -v ai-bot_postgres-data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-volume-$(date +%Y%m%d).tar.gz /data
   docker run --rm -v ai-bot_grafana-storage:/data -v $(pwd):/backup alpine tar czf /backup/grafana-volume-$(date +%Y%m%d).tar.gz /data
   ```

4. **Используйте reverse proxy** (nginx) для HTTPS, rate limiting и load balancing при масштабировании

   Пример конфигурации nginx для балансировки между несколькими экземплярами:
   ```nginx
   upstream aibot {
       least_conn;
       server localhost:8080;
       server localhost:8081;
       server localhost:8082;
   }
   
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://aibot;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```

5. **Настройте мониторинг** и алерты в Grafana

6. **Используйте secrets management** (Docker Secrets, HashiCorp Vault)

