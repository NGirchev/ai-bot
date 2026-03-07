package ru.girchev.aibot.telegram.service;

import ru.girchev.aibot.bulkhead.service.IUserPriorityService;
import ru.girchev.aibot.bulkhead.service.PriorityRequestExecutor;
import ru.girchev.aibot.common.command.CommandHandlerRegistry;
import ru.girchev.aibot.common.meter.AIBotMeterRegistry;
import ru.girchev.aibot.common.service.CommandSyncService;

public class TelegramCommandSyncService extends CommandSyncService {
    public TelegramCommandSyncService(AIBotMeterRegistry aiBotMeterRegistry, CommandHandlerRegistry commandHandlerRegistry, PriorityRequestExecutor priorityRequestExecutor, IUserPriorityService userPriorityService) {
        super(aiBotMeterRegistry, commandHandlerRegistry, priorityRequestExecutor, userPriorityService);
    }
}
