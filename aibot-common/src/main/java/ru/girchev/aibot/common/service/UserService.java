package ru.girchev.aibot.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.girchev.aibot.bulkhead.service.IUserObject;
import ru.girchev.aibot.bulkhead.service.IUserService;
import ru.girchev.aibot.common.repository.UserRepository;

import java.util.Optional;

/**
 * Универсальный сервис для работы с пользователями.
 * Ищет пользователей по id из базовой таблицы user.
 * Поддерживает как TelegramUser, так и RestUser через полиморфные запросы JPA.
 */
@Slf4j
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final UserRepository userRepository;

    @Override
    public Optional<? extends IUserObject> findById(Long userId) {
        return userRepository.findById(userId)
                .map(IUserObject.class::cast);
    }
}
