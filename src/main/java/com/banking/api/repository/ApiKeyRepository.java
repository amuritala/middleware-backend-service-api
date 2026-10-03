package com.banking.api.repository;

import com.banking.api.entity.ApiKey;
import com.banking.api.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByApiKey(String apiKey);

    List<ApiKey> findByClientAndActiveTrue(Client client);

    List<ApiKey> findByClient(Client client);

    @Query("SELECT a FROM ApiKey a WHERE a.client = :client AND a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP)")
    List<ApiKey> findActiveValidKeys(@Param("client") Client client);

    boolean existsByApiKey(String apiKey);
}
