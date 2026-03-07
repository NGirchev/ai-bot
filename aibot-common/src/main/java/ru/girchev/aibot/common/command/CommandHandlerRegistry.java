package ru.girchev.aibot.common.command;

import lombok.RequiredArgsConstructor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class CommandHandlerRegistry {

    private final List<ICommandHandler<?, ?, ?>> strategies;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends ICommandType, C extends ICommand<T>> Optional<ICommandHandler<?, ?, ?>> findHandler(C command) {
        return strategies.stream()
                .filter(s -> {
                    // Проверяем совместимость класса команды с типом, который ожидает handler
                    if (!isCommandCompatible(s, command)) {
                        return false;
                    }
                    // Используем raw type для безопасного вызова canHandle
                    // canHandle принимает ICommand<T>, а command имеет тип C extends ICommand<T>
                    return ((ICommandHandler) s).canHandle(command);
                })
                .min(Comparator.comparingInt(ICommandHandler::priority));
    }

    /**
     * Проверяет, совместим ли класс команды с типом команды, который ожидает handler.
     * Использует рефлексию для получения второго generic параметра из интерфейса ICommandHandler (тип команды C).
     */
    private boolean isCommandCompatible(ICommandHandler<?, ?, ?> handler, ICommand<?> command) {
        Class<?> commandClass = command.getClass();
        Class<?> handlerCommandClass = getHandlerCommandClass(handler);
        
        if (handlerCommandClass != null) {
            return handlerCommandClass.isAssignableFrom(commandClass);
        }
        
        // Если не удалось определить тип, разрешаем вызов canHandle (fallback)
        return true;
    }

    /**
     * Получает класс команды из generic параметров handler'а через рефлексию.
     * Возвращает второй generic параметр из интерфейса ICommandHandler (тип команды C).
     */
    private Class<?> getHandlerCommandClass(ICommandHandler<?, ?, ?> handler) {
        // Получаем generic типы из интерфейса ICommandHandler
        Type[] interfaces = handler.getClass().getGenericInterfaces();
        for (Type iface : interfaces) {
            if (iface instanceof ParameterizedType paramType 
                    && paramType.getRawType() == ICommandHandler.class) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length >= 2 && typeArgs[1] instanceof Class<?> handlerCommandClass) {
                    return handlerCommandClass;
                }
            }
        }
        
        // Если не удалось определить тип через интерфейсы, проверяем через суперкласс
        Type superclass = handler.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length >= 2 && typeArgs[1] instanceof Class<?> handlerCommandClass) {
                return handlerCommandClass;
            }
        }
        
        return null;
    }

    /**
     * Получить список всех зарегистрированных обработчиков.
     * Используется для тестирования и отладки.
     *
     * @return список обработчиков
     */
    public List<ICommandHandler<?, ?, ?>> getHandlers() {
        return strategies;
    }
}
