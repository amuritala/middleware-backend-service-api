package com.banking.api.repository;

import com.banking.api.entity.UserCreationRequest;
import com.banking.api.entity.UserCreationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserCreationRequestRepository extends JpaRepository<UserCreationRequest, UUID> {

    List<UserCreationRequest> findByStatusOrderByCreatedAtAsc(UserCreationRequestStatus status);

    List<UserCreationRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByEmailIgnoreCaseAndStatus(String email, UserCreationRequestStatus status);
}
