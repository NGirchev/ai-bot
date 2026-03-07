package ru.girchev.aibot.bulkhead.exception;

/**
 * Исключение, выбрасываемое при отказе в доступе к ресурсам.
 * Используется, когда пользователь заблокирован или когда исчерпан пул потоков.
 */
public class AccessDeniedException extends RuntimeException {

    /**
     * Создает новое исключение с указанным сообщением.
     *
     * @param message сообщение об ошибке
     */
    public AccessDeniedException(String message) {
        super(message);
    }

    /**
     * Создает новое исключение с указанным сообщением и причиной.
     *
     * @param message сообщение об ошибке
     * @param cause причина исключения
     */
    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
} 