package dev.kotryos.minischeduler.identity.internal;

import org.springframework.data.repository.Repository;

import java.util.Optional;

interface ApiKeyRepository extends Repository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
