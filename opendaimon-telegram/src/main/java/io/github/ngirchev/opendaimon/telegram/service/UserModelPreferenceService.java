package io.github.ngirchev.opendaimon.telegram.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UserModelPreferenceService {

    private final ConcurrentHashMap<Long, String> preferences = new ConcurrentHashMap<>();

    public void setPreferredModel(Long userId, String modelName) {
        preferences.put(userId, modelName);
    }

    public Optional<String> getPreferredModel(Long userId) {
        return Optional.ofNullable(preferences.get(userId));
    }

    public void clearPreference(Long userId) {
        preferences.remove(userId);
    }
}
