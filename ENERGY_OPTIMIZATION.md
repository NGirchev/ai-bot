# Оптимизация энергопотребления Ubuntu Server

Документация по оптимизации энергопотребления Ubuntu Server на ноутбуке для проекта AI Bot.

## ✅ Что уже сделано

### 1. TLP (Автоматическая оптимизация энергопотребления)
```bash
sudo apt install tlp tlp-rdw
sudo systemctl enable tlp
sudo systemctl start tlp
```
**Статус**: ✅ Установлено и запущено  
**Эффект**: Автоматическая оптимизация CPU, дисков, сети, USB  
**Откат**: `sudo systemctl disable tlp && sudo systemctl stop tlp`

### 2. Отключение Bluetooth
```bash
sudo systemctl disable bluetooth
```
**Статус**: ✅ Отключено  
**Эффект**: Экономия энергии, если Bluetooth не используется  
**Откат**: `sudo systemctl enable bluetooth && sudo systemctl start bluetooth`

### 3. Установка cpufrequtils
```bash
sudo apt install cpufrequtils
```
**Статус**: ✅ Установлено  
**Эффект**: Возможность управления частотой CPU  
**Использование**: 
- `sudo cpufreq-set -g ondemand` - баланс производительности/энергопотребления
- `sudo cpufreq-set -g powersave` - максимальная экономия
- `sudo cpufreq-set -g performance` - максимальная производительность

### 4. Отключение режимов сна/гибернации
```bash
sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
```
**Статус**: ✅ Отключено  
**Эффект**: Предотвращает случайное засыпание системы  
**Откат**: `sudo systemctl unmask sleep.target suspend.target hibernate.target hybrid-sleep.target`

### 5. Настройка systemd-logind
```bash
sudo nano /etc/systemd/logind.conf
# Настроено поведение при закрытии крышки ноутбука
```
**Статус**: ✅ Настроено  
**Файл**: `/etc/systemd/logind.conf`  
**Откат**: Вернуть настройки по умолчанию в `/etc/systemd/logind.conf`

## 🔧 Что можно оптимизировать дополнительно

### 1. CPU Governor (режим управления частотой)
**Текущее состояние**: cpufrequtils установлен, но режим не установлен явно

**Рекомендация**: Установить режим `ondemand` (баланс)
```bash
# Установить ondemand (рекомендуется для разработки)
sudo cpufreq-set -g ondemand

# Проверить текущий режим
cpufreq-info

# Или через systemd
sudo systemctl enable cpupower
sudo cpupower frequency-set -g ondemand
```

**Альтернативы**:
- `powersave` - максимальная экономия (медленнее при нагрузке)
- `performance` - максимальная производительность (больше энергопотребление)
- `conservative` - плавное переключение частот

### 2. Отключение ненужных сервисов
**Проверить и отключить**:
```bash
# Посмотреть все запущенные сервисы
sudo systemctl list-units --type=service --state=running

# Примеры сервисов, которые можно отключить (если не используются):
sudo systemctl disable snapd  # Если не используете snap
sudo systemctl disable avahi-daemon  # Если не нужен mDNS
sudo systemctl disable cups  # Если нет принтера
sudo systemctl disable ModemManager  # Если нет модема
```

### 3. Оптимизация дисков
```bash
# Добавить noatime в /etc/fstab для уменьшения записи на диск
sudo nano /etc/fstab
# Изменить строки с дисками, добавив noatime:
# /dev/sda1 / ext4 defaults,noatime 0 1

# Отключить индексацию файлов (если не нужна)
sudo systemctl disable tracker-extract tracker-miner-fs tracker-store
```

### 4. Сетевая оптимизация
```bash
# Отключить Wake-on-LAN (если не нужен)
sudo ethtool -s eth0 wol d

# Для WiFi (если используется)
sudo iw dev wlan0 set power_save on
```

### 5. Оптимизация Docker контейнеров
```bash
# Остановить неиспользуемые контейнеры
docker ps -a
docker stop <container_id>

# Очистить неиспользуемые ресурсы
docker system prune -a

# В docker-compose.yml можно добавить лимиты:
# deploy:
#   resources:
#     limits:
#       cpus: '1.0'
#       memory: 512M
```

### 6. Настройка ядра для энергосбережения
```bash
# Добавить параметры ядра
sudo nano /etc/default/grub
# В GRUB_CMDLINE_LINUX_DEFAULT добавить:
# intel_pstate=passive processor.max_cstate=2

sudo update-grub
sudo reboot
```

### 7. Мониторинг энергопотребления
```bash
# Установить powertop для анализа
sudo apt install powertop

# Калибровка (один раз)
sudo powertop --calibrate

# Просмотр что потребляет энергию
sudo powertop
```

## ⚠️ Что может потребоваться вернуть обратно

### 1. Режимы сна/гибернации
**Если нужно вернуть возможность засыпания**:
```bash
sudo systemctl unmask sleep.target suspend.target hibernate.target hybrid-sleep.target
sudo systemctl restart systemd-logind
```

**Проверка**:
```bash
sudo systemctl suspend  # Должно работать
```

### 2. Bluetooth
**Если понадобится Bluetooth**:
```bash
sudo systemctl enable bluetooth
sudo systemctl start bluetooth
```

### 3. CPU Governor
**Если нужна максимальная производительность**:
```bash
sudo cpufreq-set -g performance
```

**Если нужна максимальная экономия**:
```bash
sudo cpufreq-set -g powersave
```

### 4. TLP
**Если нужно временно отключить TLP**:
```bash
sudo tlp stop
```

**Включить обратно**:
```bash
sudo tlp start
```

**Полностью отключить**:
```bash
sudo systemctl disable tlp
sudo systemctl stop tlp
```

### 5. Отключенные сервисы
**Вернуть сервис**:
```bash
sudo systemctl enable <service-name>
sudo systemctl start <service-name>
```

## 📊 Мониторинг энергопотребления

### 1. PowerTOP (Основной инструмент)

**Установка**:
```bash
sudo apt install powertop
```

**Калибровка (один раз, занимает ~5 минут)**:
```bash
sudo powertop --calibrate
# Система будет мигать экраном и тестировать различные компоненты
```

**Интерактивный режим**:
```bash
sudo powertop
# Показывает:
# - Топ процессов по энергопотреблению
# - Частоты CPU
# - Состояние устройств (USB, PCIe, WiFi)
# - Tunables (настройки, которые можно изменить)
```

**Экспорт отчёта в HTML**:
```bash
sudo powertop --html=power_report.html
# Откроет браузер с детальным отчётом
```

**Автоматический режим (для сервера)**:
```bash
# Генерировать отчёт каждые 10 секунд
sudo powertop --time=10 --html=power_report.html

# Или в фоне
nohup sudo powertop --time=60 --html=power_report.html &
```

### 2. TLP Statistics

**Базовый статус**:
```bash
sudo tlp-stat -s
# Показывает: статус TLP, режим работы (AC/BAT), уровень батареи
```

**Детальная информация**:
```bash
# Все настройки TLP
sudo tlp-stat -c

# Информация о батарее
sudo tlp-stat -b

# Информация о CPU
sudo tlp-stat -p

# Информация о дисках
sudo tlp-stat -d

# Информация о температуре
sudo tlp-stat -t

# Полная статистика
sudo tlp-stat
```

**Экспорт в файл**:
```bash
sudo tlp-stat > tlp_report.txt
```

### 3. CPU Frequency и Governor

**Текущие частоты CPU**:
```bash
# Через cpufreq-info
cpufreq-info

# Или через sysfs
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq

# Текущий governor
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor
```

**Мониторинг в реальном времени**:
```bash
# Следить за частотами каждую секунду
watch -n 1 'cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq'

# Или через cpupower
sudo apt install linux-tools-common linux-tools-generic
watch -n 1 'cpupower frequency-info'
```

### 4. Мониторинг батареи (если есть)

**Через upower**:
```bash
# Установить
sudo apt install upower

# Информация о батарее
upower -i /org/freedesktop/UPower/devices/battery_BAT0

# Мониторинг в реальном времени
watch -n 1 'upower -i /org/freedesktop/UPower/devices/battery_BAT0 | grep -E "(percentage|energy-rate|time)"'
```

**Через sysfs**:
```bash
# Уровень заряда
cat /sys/class/power_supply/BAT0/capacity

# Статус (Charging/Discharging)
cat /sys/class/power_supply/BAT0/status

# Текущее потребление (в мкВт)
cat /sys/class/power_supply/BAT0/power_now

# Оставшееся время
cat /sys/class/power_supply/BAT0/time_to_empty
```

**Скрипт для мониторинга батареи**:
```bash
#!/bin/bash
# Сохранить как monitor_battery.sh
while true; do
    clear
    echo "=== Мониторинг батареи ==="
    echo "Уровень: $(cat /sys/class/power_supply/BAT0/capacity)%"
    echo "Статус: $(cat /sys/class/power_supply/BAT0/status)"
    echo "Потребление: $(($(cat /sys/class/power_supply/BAT0/power_now) / 1000000))W"
    echo "Время до разряда: $(cat /sys/class/power_supply/BAT0/time_to_empty) минут"
    echo "Время: $(date '+%H:%M:%S')"
    sleep 5
done
```

### 5. Мониторинг процессов и ресурсов

**Топ процессов по CPU**:
```bash
# Установить htop (более удобный чем top)
sudo apt install htop

# Запустить
htop

# Или через top
top

# Топ 10 процессов по CPU
ps aux --sort=-%cpu | head -11

# Топ 10 процессов по памяти
ps aux --sort=-%mem | head -11
```

**Мониторинг дисков**:
```bash
# Установить iotop
sudo apt install iotop

# Мониторинг I/O в реальном времени
sudo iotop

# Статистика дисков
iostat -x 1
```

**Мониторинг сети**:
```bash
# Установить iftop
sudo apt install iftop

# Мониторинг сетевого трафика
sudo iftop

# Статистика сети
iftop -i eth0 -t -s 10
```

### 6. Температура компонентов

**Температура CPU**:
```bash
# Установить sensors
sudo apt install lm-sensors

# Инициализация (один раз)
sudo sensors-detect

# Просмотр температуры
sensors

# Только CPU
sensors | grep -i cpu

# Мониторинг в реальном времени
watch -n 1 sensors
```

**Температура через sysfs**:
```bash
# Температура CPU (в миллиградусах)
cat /sys/class/thermal/thermal_zone*/temp

# В градусах
for i in /sys/class/thermal/thermal_zone*/temp; do
    echo "$i: $(($(cat $i) / 1000))°C"
done
```

### 7. Системные метрики

**Через systemd-cgtop**:
```bash
# Мониторинг cgroups (CPU, память, I/O)
systemd-cgtop

# Или
systemctl status
```

**Через vmstat**:
```bash
# Статистика системы каждую секунду
vmstat 1

# Показывает: CPU, память, swap, I/O, прерывания
```

**Через iostat**:
```bash
# Установить sysstat
sudo apt install sysstat

# Статистика каждые 2 секунды
iostat -x 2
```

### 8. Создание скрипта для комплексного мониторинга

**Скрипт power_monitor.sh**:
```bash
#!/bin/bash
# Сохранить как power_monitor.sh
# chmod +x power_monitor.sh

while true; do
    clear
    echo "=== Мониторинг энергопотребления ==="
    echo ""
    
    # CPU
    echo "=== CPU ==="
    echo "Governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"
    echo "Частота: $(($(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) / 1000)) MHz"
    echo "Загрузка: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}')"
    echo ""
    
    # Батарея (если есть)
    if [ -f /sys/class/power_supply/BAT0/capacity ]; then
        echo "=== Батарея ==="
        echo "Уровень: $(cat /sys/class/power_supply/BAT0/capacity)%"
        echo "Статус: $(cat /sys/class/power_supply/BAT0/status)"
        if [ -f /sys/class/power_supply/BAT0/power_now ]; then
            echo "Потребление: $(($(cat /sys/class/power_supply/BAT0/power_now) / 1000000))W"
        fi
        echo ""
    fi
    
    # Температура
    if command -v sensors &> /dev/null; then
        echo "=== Температура ==="
        sensors | grep -E "(CPU|Core|Package)" | head -3
        echo ""
    fi
    
    # TLP статус
    if command -v tlp-stat &> /dev/null; then
        echo "=== TLP ==="
        sudo tlp-stat -s | grep -E "(Mode|Battery|AC)" | head -3
        echo ""
    fi
    
    # Топ процессов по CPU
    echo "=== Топ процессов (CPU) ==="
    ps aux --sort=-%cpu | head -6
    echo ""
    
    echo "Время: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Обновление каждые 5 секунд (Ctrl+C для выхода)"
    
    sleep 5
done
```

**Использование**:
```bash
chmod +x power_monitor.sh
./power_monitor.sh
```

### 9. Логирование энергопотребления

**Создать скрипт для логирования**:
```bash
#!/bin/bash
# Сохранить как log_power.sh
# chmod +x log_power.sh

LOG_FILE="/var/log/power_monitor.log"

while true; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    
    # CPU частота
    CPU_FREQ=$(($(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) / 1000))
    CPU_GOV=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)
    
    # Батарея
    if [ -f /sys/class/power_supply/BAT0/capacity ]; then
        BAT_CAP=$(cat /sys/class/power_supply/BAT0/capacity)
        BAT_STAT=$(cat /sys/class/power_supply/BAT0/status)
        if [ -f /sys/class/power_supply/BAT0/power_now ]; then
            BAT_PWR=$(($(cat /sys/class/power_supply/BAT0/power_now) / 1000000))
        else
            BAT_PWR="N/A"
        fi
    else
        BAT_CAP="N/A"
        BAT_STAT="N/A"
        BAT_PWR="N/A"
    fi
    
    # Записать в лог
    echo "$TIMESTAMP | CPU: ${CPU_FREQ}MHz ($CPU_GOV) | Battery: ${BAT_CAP}% ($BAT_STAT) ${BAT_PWR}W" >> $LOG_FILE
    
    sleep 60  # Каждую минуту
done
```

**Запуск в фоне**:
```bash
nohup ./log_power.sh &
```

**Просмотр логов**:
```bash
tail -f /var/log/power_monitor.log
```

### 10. Автоматические отчёты

**Ежедневный отчёт через cron**:
```bash
# Добавить в crontab
sudo crontab -e

# Генерировать отчёт каждый день в 23:00
0 23 * * * /usr/bin/sudo powertop --html=/var/log/power_report_$(date +\%Y\%m\%d).html --time=60
```

## 📊 Проверка эффективности

### Быстрая проверка текущего состояния
```bash
# Статус TLP
sudo tlp-stat -s

# Текущие настройки CPU
sudo tlp-stat -p
cpufreq-info

# Потребление энергии (если установлен powertop)
sudo powertop

# Статус всех сервисов
sudo systemctl list-units --type=service --state=running
```

### Проверка логов
```bash
# Проверить логи suspend/sleep
journalctl -b | grep -i suspend

# Логи systemd-logind
journalctl -u systemd-logind

# Логи TLP
journalctl -u tlp
```

## 🔍 Проблемы и решения

### Проблема: vbetool dpms off не работает
**Причина**: vbetool может не работать на сервере без GUI или с некоторыми видеокартами  
**Решение**: Использовать другие методы оптимизации (TLP, CPU governor)

### Проблема: nvidia-smi не установлена
**Причина**: NVIDIA драйверы не установлены (нормально для Ubuntu Server)  
**Решение**: GPU уже не потребляет энергию через драйверы, дополнительная оптимизация не нужна

### Проблема: Система всё равно потребляет много энергии
**Диагностика**:
```bash
# Проверить что потребляет CPU
top
htop

# Проверить активные процессы
ps aux --sort=-%cpu | head -20

# Проверить активность дисков
sudo iotop

# Проверить сетевую активность
sudo iftop
```

## 📝 Заметки

- **Дата последнего обновления**: 2024
- **Система**: Ubuntu Server на ноутбуке
- **Проект**: AI Bot (Spring Boot + Docker + PostgreSQL)

## 🔗 Полезные ссылки

- [TLP Documentation](https://linrunner.de/tlp/)
- [Ubuntu Power Management](https://help.ubuntu.com/community/PowerManagement)
- [CPU Frequency Scaling](https://wiki.archlinux.org/title/CPU_frequency_scaling)