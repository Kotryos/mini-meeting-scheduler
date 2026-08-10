package dev.kotryos.minischeduler.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_key")
class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    long userId() {
        return userId;
    }

    String role() {
        return role;
    }
}
