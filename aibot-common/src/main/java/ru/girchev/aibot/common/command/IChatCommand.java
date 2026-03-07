package ru.girchev.aibot.common.command;

public interface IChatCommand<T extends ICommandType> extends ICommand<T> {
    String userText();
    boolean stream();
}
