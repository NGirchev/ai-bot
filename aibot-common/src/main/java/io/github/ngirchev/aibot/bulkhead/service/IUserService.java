package io.github.ngirchev.aibot.bulkhead.service;

import java.util.Optional;

public interface IUserService {
    Optional<IUserObject> findById(Long userId);
}
