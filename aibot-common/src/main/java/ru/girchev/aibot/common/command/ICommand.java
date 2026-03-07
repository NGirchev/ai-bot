package ru.girchev.aibot.common.command;

public interface ICommand<T extends ICommandType> {
    Long userId();
    T commandType();
}
