package ru.girchev.aibot.bulkhead.service;

import io.github.resilience4j.bulkhead.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import ru.girchev.aibot.bulkhead.config.BulkHeadProperties;
import ru.girchev.aibot.bulkhead.exception.AccessDeniedException;
import ru.girchev.aibot.bulkhead.model.UserPriority;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Сервис для выполнения запросов с учетом приоритета пользователя.
 * Реализует паттерн Bulkhead для разделения ресурсов между пользователями разных приоритетов.
 */
@Slf4j
@RequiredArgsConstructor
public class PriorityRequestExecutor implements AutoCloseable {

    private final IUserPriorityService userPriorityService;
    private final BulkHeadProperties bulkHeadProperties;

    private Bulkhead vipBulkhead;
    private Bulkhead regularBulkhead;
    private Bulkhead adminBulkhead;

    private ExecutorService taskExecutor;

    /**
     * Инициализация пулов потоков (bulkhead) при запуске приложения.
     */
    @PostConstruct
    public void init() {
        BulkHeadProperties.BulkheadInstance vipInstance = resolveInstance(
                UserPriority.VIP,
                10,
                Duration.ofSeconds(1)
        );
        BulkheadConfig vipConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(vipInstance.maxConcurrentCalls())
                .maxWaitDuration(vipInstance.maxWaitDuration())
                .build();

        BulkHeadProperties.BulkheadInstance regularInstance = resolveInstance(
                UserPriority.REGULAR,
                5,
                Duration.ofMillis(500)
        );
        BulkheadConfig regularConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(regularInstance.maxConcurrentCalls())
                .maxWaitDuration(regularInstance.maxWaitDuration())
                .build();

        BulkHeadProperties.BulkheadInstance adminInstance = resolveInstance(
                UserPriority.ADMIN,
                20,
                Duration.ofSeconds(1)
        );
        BulkheadConfig adminConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(adminInstance.maxConcurrentCalls())
                .maxWaitDuration(adminInstance.maxWaitDuration())
                .build();

        // Создаем реестр bulkhead
        BulkheadRegistry registry = BulkheadRegistry.of(BulkheadConfig.ofDefaults());

        // Регистрируем bulkhead для разных типов пользователей
        this.vipBulkhead = registry.bulkhead("vipUserBulkhead", vipConfig);
        this.regularBulkhead = registry.bulkhead("regularUserBulkhead", regularConfig);
        this.adminBulkhead = registry.bulkhead("adminUserBulkhead", adminConfig);

        initTaskExecutor(vipConfig, regularConfig, adminConfig);

        log.info("Инициализированы пулы потоков: Admin ({} потоков), VIP ({} потоков), Regular ({} потоков)",
                adminConfig.getMaxConcurrentCalls(),
                vipConfig.getMaxConcurrentCalls(),
                regularConfig.getMaxConcurrentCalls());
    }

    private void initTaskExecutor(BulkheadConfig vipConfig, BulkheadConfig regularConfig, BulkheadConfig adminConfig) {
        if (this.taskExecutor != null) {
            return;
        }

        int configuredThreads = bulkHeadProperties.getExecutorThreads();
        int threads = configuredThreads > 0
                ? configuredThreads
                : Math.max(1,
                vipConfig.getMaxConcurrentCalls()
                        + regularConfig.getMaxConcurrentCalls()
                        + adminConfig.getMaxConcurrentCalls());

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("priority-request-executor-" + counter.incrementAndGet());
                thread.setDaemon(false);
                return thread;
            }
        };

        this.taskExecutor = Executors.newFixedThreadPool(threads, threadFactory);
        log.info("Инициализирован внутренний пул PriorityRequestExecutor ({} потоков)", threads);
    }

    @PreDestroy
    public void shutdown() {
        if (taskExecutor == null) {
            return;
        }
        taskExecutor.shutdownNow();
        try {
            taskExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            taskExecutor = null;
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Выполняет запрос с учетом приоритета пользователя.
     *
     * @param userId идентификатор пользователя
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return результат выполнения задачи
     * @throws AccessDeniedException если пользователь заблокирован или исчерпан пул потоков
     */
    public <T> T executeRequest(Long userId, Callable<T> task) throws Exception {
        UserPriority priority = userPriorityService.getUserPriority(userId);
        log.info("Выполнение запроса для пользователя {} с приоритетом {}", userId, priority);

        if (priority == null) {
            log.error("Неизвестный приоритет пользователя: null");
            throw new IllegalStateException("Неизвестный приоритет пользователя: null");
        }

        switch (priority) {
            case ADMIN:
                return executeInAdminBulkhead(task);
            case VIP:
                return executeInVipBulkhead(task);
            case REGULAR:
                return executeInRegularBulkhead(task);
            case BLOCKED:
                log.error("Доступ запрещен для заблокированного пользователя {}", userId);
                throw new AccessDeniedException("Пользователь заблокирован. Доступ запрещен.");
            default:
                log.error("Неизвестный приоритет пользователя: {}", priority);
                throw new IllegalStateException("Неизвестный приоритет пользователя: " + priority);
        }
    }

    /**
     * Асинхронно выполняет запрос с учетом приоритета пользователя.
     *
     * @param userId идентификатор пользователя
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return CompletionStage с результатом выполнения задачи
     */
    public <T> CompletionStage<T> executeRequestAsync(Long userId, Supplier<T> task) {
        UserPriority priority = userPriorityService.getUserPriority(userId);
        log.info("Асинхронное выполнение запроса для пользователя {} с приоритетом {}", userId, priority);

        if (priority == null) {
            log.error("Неизвестный приоритет пользователя: null");
            CompletableFuture<T> nullFuture = new CompletableFuture<>();
            nullFuture.completeExceptionally(new IllegalStateException("Неизвестный приоритет пользователя: null"));
            return nullFuture;
        }

        switch (priority) {
            case ADMIN:
                return executeInAdminBulkheadAsync(task);
            case VIP:
                return executeInVipBulkheadAsync(task);
            case REGULAR:
                return executeInRegularBulkheadAsync(task);
            case BLOCKED:
                log.error("Доступ запрещен для заблокированного пользователя {}", userId);
                CompletableFuture<T> blockedFuture = new CompletableFuture<>();
                blockedFuture.completeExceptionally(new AccessDeniedException("Пользователь заблокирован. Доступ запрещен."));
                return blockedFuture;
            default:
                log.error("Неизвестный приоритет пользователя: {}", priority);
                CompletableFuture<T> unknownFuture = new CompletableFuture<>();
                unknownFuture.completeExceptionally(new IllegalStateException("Неизвестный приоритет пользователя: " + priority));
                return unknownFuture;
        }
    }

    /**
     * Выполняет задачу в пуле потоков для VIP пользователей.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return результат выполнения задачи
     * @throws Exception если произошла ошибка при выполнении задачи или исчерпан пул потоков
     */
    private <T> T executeInVipBulkhead(Callable<T> task) throws Exception {
        try {
            return executeInExecutor(vipBulkhead, task);
        } catch (Exception e) {
            log.error("Ошибка при выполнении запроса в VIP пуле: {} {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Выполняет задачу в пуле потоков для обычных пользователей.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return результат выполнения задачи
     * @throws Exception если произошла ошибка при выполнении задачи или исчерпан пул потоков
     */
    private <T> T executeInRegularBulkhead(Callable<T> task) throws Exception {
        try {
            return executeInExecutor(regularBulkhead, task);
        } catch (Exception e) {
            log.error("Ошибка при выполнении запроса в обычном пуле: {} {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Асинхронно выполняет задачу в пуле потоков для VIP пользователей.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return CompletionStage с результатом выполнения задачи
     */
    private <T> CompletionStage<T> executeInVipBulkheadAsync(Supplier<T> task) {
        return executeInExecutorAsync(vipBulkhead, task);
    }

    /**
     * Выполняет задачу в пуле потоков для администраторов.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return результат выполнения задачи
     * @throws Exception если произошла ошибка при выполнении задачи или исчерпан пул потоков
     */
    private <T> T executeInAdminBulkhead(Callable<T> task) throws Exception {
        try {
            return executeInExecutor(adminBulkhead, task);
        } catch (Exception e) {
            log.error("Ошибка при выполнении запроса в Admin пуле: {} {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Асинхронно выполняет задачу в пуле потоков для администраторов.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return CompletionStage с результатом выполнения задачи
     */
    private <T> CompletionStage<T> executeInAdminBulkheadAsync(Supplier<T> task) {
        return executeInExecutorAsync(adminBulkhead, task);
    }

    /**
     * Асинхронно выполняет задачу в пуле потоков для обычных пользователей.
     *
     * @param task задача, которую нужно выполнить
     * @param <T> тип результата задачи
     * @return CompletionStage с результатом выполнения задачи
     */
    private <T> CompletionStage<T> executeInRegularBulkheadAsync(Supplier<T> task) {
        return executeInExecutorAsync(regularBulkhead, task);
    }

    private <T> T executeInExecutor(Bulkhead bulkhead, Callable<T> task) throws Exception {
        if (taskExecutor == null) {
            return Bulkhead.decorateCallable(bulkhead, task).call();
        }

        bulkhead.acquirePermission();
        Future<T> future = taskExecutor.submit(() -> {
            try {
                return task.call();
            } finally {
                bulkhead.onComplete();
            }
        });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw unwrapExecutionException(e);
        }
    }

    private <T> CompletionStage<T> executeInExecutorAsync(Bulkhead bulkhead, Supplier<T> task) {
        if (taskExecutor == null) {
            Supplier<CompletionStage<T>> decoratedSupplier = () -> CompletableFuture.completedFuture(task.get());
            return Bulkhead.decorateSupplier(bulkhead, decoratedSupplier).get();
        }

        return CompletableFuture.supplyAsync(() -> {
            bulkhead.acquirePermission();
            try {
                return task.get();
            } finally {
                bulkhead.onComplete();
            }
        }, taskExecutor);
    }

    private static Exception unwrapExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception ex) {
            return ex;
        }
        if (cause instanceof Error err) {
            throw err;
        }
        return new RuntimeException(cause);
    }

    private BulkHeadProperties.BulkheadInstance resolveInstance(
            UserPriority priority,
            int defaultMaxConcurrentCalls,
            Duration defaultMaxWaitDuration) {
        BulkHeadProperties.BulkheadInstance instance = bulkHeadProperties.getInstances().get(priority);
        if (instance != null && instance.maxConcurrentCalls() > 0 && instance.maxWaitDuration() != null) {
            return instance;
        }

        return new BulkHeadProperties.BulkheadInstance(
                defaultMaxConcurrentCalls, defaultMaxWaitDuration);
    }
}
