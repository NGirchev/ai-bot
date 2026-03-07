package ru.girchev.aibot.bulkhead.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.girchev.aibot.bulkhead.config.BulkHeadProperties;
import ru.girchev.aibot.bulkhead.exception.AccessDeniedException;
import ru.girchev.aibot.bulkhead.model.UserPriority;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link PriorityRequestExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class PriorityRequestExecutorTest {

    @Mock
    private IUserPriorityService userPriorityService;

    private PriorityRequestExecutor requestExecutor;

    @BeforeEach
    void setUp() {
        BulkHeadProperties bulkHeadProperties = new BulkHeadProperties();
        requestExecutor = new PriorityRequestExecutor(userPriorityService, bulkHeadProperties);
        // Вызываем метод инициализации вручную, так как @PostConstruct не вызывается в тестах
        requestExecutor.init();
    }

    @AfterEach
    void tearDown() {
        requestExecutor.close();
    }

    @Test
    void whenVipUser_thenExecuteInVipBulkhead() throws Exception {
        // Arrange
        Long userId = 1L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.VIP);
        Callable<String> task = () -> "VIP result";

        // Act
        String result = requestExecutor.executeRequest(userId, task);

        // Assert
        assertEquals("VIP result", result);
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenAdminUser_thenExecuteInAdminBulkhead() throws Exception {
        // Arrange
        Long userId = 10L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.ADMIN);
        Callable<String> task = () -> "Admin result";

        // Act
        String result = requestExecutor.executeRequest(userId, task);

        // Assert
        assertEquals("Admin result", result);
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenRegularUser_thenExecuteInRegularBulkhead() throws Exception {
        // Arrange
        Long userId = 2L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.REGULAR);
        Callable<String> task = () -> "Regular result";

        // Act
        String result = requestExecutor.executeRequest(userId, task);

        // Assert
        assertEquals("Regular result", result);
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenBlockedUser_thenThrowAccessDeniedException() {
        // Arrange
        Long userId = 3L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.BLOCKED);
        Callable<String> task = () -> "Should not execute";

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> requestExecutor.executeRequest(userId, task));
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenVipUserAsync_thenExecuteInVipBulkhead() {
        // Arrange
        Long userId = 1L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.VIP);
        Supplier<String> task = () -> "VIP result";

        // Act
        CompletableFuture<String> future = requestExecutor.executeRequestAsync(userId, task)
                .toCompletableFuture();

        // Assert
        assertDoesNotThrow(() -> {
            String result = future.get();
            assertEquals("VIP result", result);
        });
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenAdminUserAsync_thenExecuteInAdminBulkhead() {
        // Arrange
        Long userId = 10L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.ADMIN);
        Supplier<String> task = () -> "Admin result";

        // Act
        CompletableFuture<String> future = requestExecutor.executeRequestAsync(userId, task)
                .toCompletableFuture();

        // Assert
        assertDoesNotThrow(() -> {
            String result = future.get();
            assertEquals("Admin result", result);
        });
        verify(userPriorityService).getUserPriority(userId);
    }
    @Test
    void whenBlockedUserAsync_thenCompleteFutureExceptionally() {
        // Arrange
        Long userId = 3L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.BLOCKED);
        Supplier<String> task = () -> "Should not execute";

        // Act
        CompletableFuture<String> future = requestExecutor.executeRequestAsync(userId, task)
                .toCompletableFuture();

        // Assert
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(AccessDeniedException.class, exception.getCause(),
                "Причиной исключения должен быть AccessDeniedException");
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void whenNullPriority_thenThrowIllegalStateException() {
        // Arrange
        Long userId = 4L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(null);
        Callable<String> task = () -> "Should not execute";

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> requestExecutor.executeRequest(userId, task));
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void testBulkheadInitialization() {
        // Проверяем, что bulkhead инициализированы правильно
        assertNotNull(requestExecutor, "RequestExecutor не должен быть null");
        
        // Проверяем, что метод init() был вызван в setUp()
        // Косвенно проверяем через вызов метода executeRequest
        Long userId = 1L;
        String expectedResult = "Test";
        Callable<String> task = () -> expectedResult;
        
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.REGULAR);
        
        try {
            String result = requestExecutor.executeRequest(userId, task);
            assertEquals(expectedResult, result, "Задача должна быть выполнена после инициализации");
        } catch (Exception e) {
            fail("Не должно быть исключения при выполнении задачи: " + e.getMessage());
        }
    }

    @Test
    @Disabled("Этот тест может быть нестабильным, так как зависит от времени выполнения")
    void testBulkheadRejection() throws Exception {
        // Arrange
        Long userId = 1L;
        when(userPriorityService.getUserPriority(userId)).thenReturn(UserPriority.REGULAR);
        
        // Создаем задачу, которая будет выполняться долго
        Callable<String> longRunningTask = () -> {
            Thread.sleep(1000); // Имитация долгой работы
            return "Результат";
        };
        
        // Запускаем максимальное количество задач (5 для REGULAR)
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            final int taskNum = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    requestExecutor.executeRequest(userId, longRunningTask);
                } catch (Exception e) {
                    // Игнорируем исключения в фоновых задачах
                }
            });
        }
        
        // Даем время на запуск задач
        Thread.sleep(100);
        
        // Act & Assert
        // Шестая задача должна быть отклонена из-за исчерпания пула
        assertThrows(Exception.class, () -> {
            requestExecutor.executeRequest(userId, () -> "Эта задача не должна выполниться");
        }, "Должно быть выброшено исключение при исчерпании пула потоков");
    }

    @Test
    void testExecuteRequest_UnknownPriority_ShouldThrowIllegalStateException() {
        // Arrange
        Long userId = 5L;
        Callable<String> task = () -> "Этот результат не должен быть возвращен";
        
        // Имитируем возврат null вместо конкретного приоритета
        when(userPriorityService.getUserPriority(userId)).thenReturn(null);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            requestExecutor.executeRequest(userId, task);
        }, "Должно быть выброшено исключение IllegalStateException для неизвестного приоритета");
        
        assertTrue(exception.getMessage().contains("Неизвестный приоритет пользователя"), 
                "Сообщение об ошибке должно содержать информацию о неизвестном приоритете");
        verify(userPriorityService).getUserPriority(userId);
    }

    @Test
    void testExecuteRequestAsync_UnknownPriority_ShouldCompleteExceptionally() {
        // Arrange
        Long userId = 5L;
        Supplier<String> task = () -> "Этот результат не должен быть возвращен";
        
        // Имитируем возврат null вместо конкретного приоритета
        when(userPriorityService.getUserPriority(userId)).thenReturn(null);

        // Act
        CompletionStage<String> resultStage = requestExecutor.executeRequestAsync(userId, task);
        
        // Assert
        CompletableFuture<String> future = resultStage.toCompletableFuture();
        assertTrue(future.isCompletedExceptionally(), "CompletableFuture должен завершиться с исключением для неизвестного приоритета");
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get, "CompletableFuture должен завершиться с исключением для неизвестного приоритета");

        assertInstanceOf(IllegalStateException.class, exception.getCause(), "Причиной исключения должен быть IllegalStateException");
        assertTrue(exception.getCause().getMessage().contains("Неизвестный приоритет пользователя"), 
                "Сообщение об ошибке должно содержать информацию о неизвестном приоритете");
        verify(userPriorityService).getUserPriority(userId);
    }
} 
