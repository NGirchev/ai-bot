package ru.girchev.aibot.bulkhead.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * NoOp реализация PriorityRequestExecutor.
 * Используется когда bulkhead выключен (ai-bot.common.bulkhead.enabled=false).
 * Просто выполняет задачи напрямую без каких-либо ограничений и приоритизации.
 */
@Slf4j
public class NoOpPriorityRequestExecutor extends PriorityRequestExecutor {

    public NoOpPriorityRequestExecutor() {
        super(null, null);
    }

    /**
     * NoOp версия не инициализирует bulkhead.
     */
    @Override
    public void init() {
        log.info("NoOpPriorityRequestExecutor: bulkhead отключен, приоритизация не применяется");
    }

    /**
     * NoOp версия не имеет ресурсов для освобождения.
     */
    @Override
    public void shutdown() {
        // Ничего не делаем
    }

    /**
     * NoOp версия не имеет ресурсов для освобождения.
     */
    @Override
    public void close() {
        // Ничего не делаем
    }

    /**
     * Просто выполняет задачу напрямую без bulkhead.
     */
    @Override
    public <T> T executeRequest(Long userId, Callable<T> task) throws Exception {
        log.debug("NoOp выполнение запроса для пользователя {} без bulkhead", userId);
        return task.call();
    }

    /**
     * Асинхронно выполняет задачу напрямую без bulkhead.
     */
    @Override
    public <T> CompletionStage<T> executeRequestAsync(Long userId, Supplier<T> task) {
        log.debug("NoOp асинхронное выполнение запроса для пользователя {} без bulkhead", userId);
        return CompletableFuture.supplyAsync(task);
    }
}
