package com.bewerbung.repository;

import com.bewerbung.entity.SessionDataEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;

@Repository
@ConditionalOnBean(DataSource.class)
public interface SessionDataRepository extends JpaRepository<SessionDataEntity, Long> {

    Optional<SessionDataEntity> findBySessionId(String sessionId);
}
