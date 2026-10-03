package com.banking.api.repository;

import com.banking.api.entity.Client;
import com.banking.api.entity.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByEmail(String email);

    Optional<Client> findByKeycloakUserId(String keycloakUserId);

    Optional<Client> findByCompanyName(String companyName);

    List<Client> findByStatus(ClientStatus status);

    @Query("SELECT c FROM Client c WHERE c.status = :status ORDER BY c.createdAt DESC")
    List<Client> findPendingClients(@Param("status") ClientStatus status);

    boolean existsByEmail(String email);

    boolean existsByRegistrationNumber(String registrationNumber);
}