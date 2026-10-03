package com.banking.api.service;

import com.banking.api.dto.DownstreamAuditResponse;
import com.banking.api.repository.DownstreamAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DownstreamAuditService {

    private final DownstreamAuditRepository repository;

    public Page<DownstreamAuditResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(audit -> new DownstreamAuditResponse(
                audit.getId(),
                audit.getUserId(),
                audit.getRequest(),
                audit.getResponse(),
                audit.getHttpStatus(),
                audit.getStartTime(),
                audit.getEndTime()
        ));
    }
}
