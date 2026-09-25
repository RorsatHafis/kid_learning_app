package com.platform.audit.service;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditEvent;
import com.platform.audit.repository.AuditEventRepository;
import com.platform.common.web.CorrelationIdFilter;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService (AuditEventRepository repository) {

        this.repository = repository;

    }

    @Transactional
    public AuditEvent record(RecordAuditEvent request) {

        Assert.hasText(request.action(), "action must not be blank");
        Assert.hasText(request.resourceType(), "resourceType must not be blank");
        Assert.notNull(request.actorType(), "actorType must not be null");
 
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        if (correlationId == null || correlationId.isBlank()) {

            correlationId = UUID.randomUUID().toString();

        }

        AuditEvent event = AuditEvent.occur(
                request.idempotencyRecordId(),
                request.actorId(),
                request.actorType(),
                request.action(),
                request.resourceType(),
                request.resourceId(),
                correlationId,
                request.metadata());
 
        return repository.save(event);

    }
    
}
