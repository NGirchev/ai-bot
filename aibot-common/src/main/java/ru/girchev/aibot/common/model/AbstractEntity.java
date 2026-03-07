package ru.girchev.aibot.common.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.util.Objects;

/**
 * Базовый класс для всех Entity с общей логикой equals и hashCode
 * @param <ID> тип идентификатора сущности
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractEntity<ID extends Serializable> implements Serializable {
    
    /**
     * Получение идентификатора сущности
     * @return идентификатор сущности
     */
    public abstract ID getId();
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractEntity<?> that = (AbstractEntity<?>) o;
        return Objects.equals(getId(), that.getId());
    }
    
    @Override
    public int hashCode() {
        return getId() != null ? getId().hashCode() : 0;
    }
} 