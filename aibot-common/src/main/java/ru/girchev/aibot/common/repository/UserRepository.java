package ru.girchev.aibot.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.girchev.aibot.common.model.User;

/**
 * Репозиторий для работы с базовой таблицей user
 * Поддерживает полиморфные запросы для TelegramUser и RestUser
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}

