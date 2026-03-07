package ru.girchev.aibot.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.girchev.aibot.common.model.Bugreport;
import ru.girchev.aibot.common.model.BugreportType;
import ru.girchev.aibot.common.model.User;

import java.util.List;

@Repository
public interface BugreportRepository extends JpaRepository<Bugreport, Long> {
    
    List<Bugreport> findByUserOrderByCreatedAtDesc(User user);
    
    List<Bugreport> findByTypeOrderByCreatedAtDesc(BugreportType type);
    
    List<Bugreport> findByUserAndTypeOrderByCreatedAtDesc(User user, BugreportType type);
}

