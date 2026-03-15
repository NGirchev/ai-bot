package io.github.ngirchev.opendaimon.telegram.service;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import io.github.ngirchev.opendaimon.telegram.model.TelegramUser;
import io.github.ngirchev.opendaimon.telegram.repository.TelegramUserRepository;

import java.util.Optional;

@RequiredArgsConstructor
public class UserModelPreferenceService {

    private final TelegramUserRepository telegramUserRepository;

    public void setPreferredModel(Long userId, String modelName) {
        telegramUserRepository.findById(userId).ifPresent(user -> {
            user.setPreferredModelId(modelName);
            telegramUserRepository.save(user);
        });
    }

    public Optional<String> getPreferredModel(Long userId) {
        return telegramUserRepository.findById(userId)
                .map(TelegramUser::getPreferredModelId)
                .filter(StringUtils::hasText);
    }

    public void clearPreference(Long userId) {
        telegramUserRepository.findById(userId).ifPresent(user -> {
            user.setPreferredModelId(null);
            telegramUserRepository.save(user);
        });
    }
}
