package com.example.expensetracker.demo.session;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("prod")
public class DemoAccessTokenRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(DemoAccessToken accessToken) {
        entityManager.persist(accessToken);
        entityManager.flush();
    }
}
