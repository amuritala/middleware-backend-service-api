package com.banking.api.repository;

import com.banking.api.entity.RoleChangeApplication;
import com.banking.api.entity.RoleChangeApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleChangeApplicationRepository extends JpaRepository<RoleChangeApplication, UUID> {

    List<RoleChangeApplication> findByStatusOrderByCreatedAtAsc(RoleChangeApplicationStatus status);

    List<RoleChangeApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, RoleChangeApplicationStatus status);
}
