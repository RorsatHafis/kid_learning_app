package com.platform.audit.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.entity.AuditEvent;
import com.platform.audit.repository.AuditEventRepository;
import com.platform.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
 
import java.util.Map;
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class AuditServiceTests {
 
    @Mock
    private AuditEventRepository repository;
 
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }
 
    @Test
    void usesTheInboundRequestsCorrelationIdWhenPresent() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "request-correlation-id");
        AuditService service = new AuditService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
 
        AuditEvent event = service.record(new RecordAuditEvent(
                null, UUID.randomUUID(), AuditActorType.PARENT, "ATTEMPT_SUBMITTED", "ATTEMPT", null, Map.of()));
 
        assertThat(event.getCorrelationId()).isEqualTo("request-correlation-id");
    }
 
    @Test
    void mintsAFreshCorrelationIdWhenNoneIsInMdc() {
        AuditService service = new AuditService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
 
        AuditEvent event = service.record(new RecordAuditEvent(
                null, null, AuditActorType.SYSTEM, "REVIEW_SCHEDULED", "REVIEW_SCHEDULE", null, null));
 
        assertThat(event.getCorrelationId()).isNotBlank();
    }
 
    @Test
    void rejectsABlankAction() {
        AuditService service = new AuditService(repository);
 
        assertThatThrownBy(() -> service.record(new RecordAuditEvent(
                null, null, AuditActorType.SYSTEM, "  ", "REVIEW_SCHEDULE", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
 
    @Test
    void rejectsAMissingActorType() {
        AuditService service = new AuditService(repository);
 
        assertThatThrownBy(() -> service.record(new RecordAuditEvent(
                null, null, null, "REVIEW_SCHEDULED", "REVIEW_SCHEDULE", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
 
}
 
