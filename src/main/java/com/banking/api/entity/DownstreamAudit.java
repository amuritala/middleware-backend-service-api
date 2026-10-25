package com.banking.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "downstream_audit")
@Getter
@Setter
@NoArgsConstructor
public class DownstreamAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "operation")
    private String operation;

    @Column(name = "request_blob", columnDefinition = "TEXT", nullable = false)
    private String request;

    @Column(name = "response_blob", columnDefinition = "TEXT")
    private String response;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
}
