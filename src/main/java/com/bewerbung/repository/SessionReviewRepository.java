package com.bewerbung.repository;

import com.bewerbung.entity.SessionReviewEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
@ConditionalOnBean(DataSource.class)
public interface SessionReviewRepository extends JpaRepository<SessionReviewEntity, Long> {

    List<SessionReviewEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
