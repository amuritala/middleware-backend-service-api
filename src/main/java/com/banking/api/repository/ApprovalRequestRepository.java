package com.banking.api.repository;

import com.banking.api.entity.ApprovalRequest;
import com.banking.api.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequest> findAllByOrderByCreatedAtDesc();

    List<ApprovalRequest> findByInitiatedByOrderByCreatedAtDesc(String keycloakUserId);

    boolean existsByStatusAndInitiatedBy(ApprovalStatus status, String keycloakUserId);
}
