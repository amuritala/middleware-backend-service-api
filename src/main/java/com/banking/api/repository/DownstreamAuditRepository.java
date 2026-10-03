package com.banking.api.repository;

import com.banking.api.entity.DownstreamAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DownstreamAuditRepository extends JpaRepository<DownstreamAudit, UUID> {
}
