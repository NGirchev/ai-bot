package ru.girchev.aibot.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.model.Bugreport;
import ru.girchev.aibot.common.model.BugreportType;
import ru.girchev.aibot.common.model.User;
import ru.girchev.aibot.common.repository.BugreportRepository;

@Slf4j
@RequiredArgsConstructor
public class BugreportService {
    
    private final BugreportRepository bugreportRepository;
    
    @Transactional
    public Bugreport saveBug(User user, String text) {
        log.debug("Saving bug report for user: {}", user.getId());
        Bugreport bugreport = new Bugreport();
        bugreport.setUser(user);
        bugreport.setText(text);
        bugreport.setType(BugreportType.BUG);
        return bugreportRepository.save(bugreport);
    }

    @Transactional
    public Bugreport saveImprovementProposal(User user, String text) {
        log.debug("Saving improvement proposal for user: {}", user.getId());
        Bugreport bugreport = new Bugreport();
        bugreport.setUser(user);
        bugreport.setText(text);
        bugreport.setType(BugreportType.IMPROVEMENT);
        return bugreportRepository.save(bugreport);
    }
}
