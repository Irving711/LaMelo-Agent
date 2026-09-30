package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTaskLeaseServiceTest {
    private final LaMeloAgentDocumentTaskMapper mapper = mock(LaMeloAgentDocumentTaskMapper.class);
    private final DocumentTaskLeaseService service = new DocumentTaskLeaseService(mapper);

    @Test
    void claimsOnlyWhenConditionalUpdateChangesOneRow() {
        when(mapper.tryAcquireLease(42L, "worker", 900L)).thenReturn(1, 0);

        assertThat(service.tryAcquire(42L, "worker", Duration.ofSeconds(900))).isTrue();
        assertThat(service.tryAcquire(42L, "worker", Duration.ofSeconds(900))).isFalse();
        verify(mapper, org.mockito.Mockito.times(2)).tryAcquireLease(42L, "worker", 900L);
    }

    @Test
    void renewsAndReleasesOnlyForTheCurrentOwner() {
        when(mapper.renewLease(42L, "worker", 900L)).thenReturn(1);
        when(mapper.releaseLease(42L, "worker")).thenReturn(1);
        when(mapper.hasValidLease(42L, "worker")).thenReturn(1);

        assertThat(service.renew(42L, "worker", Duration.ofSeconds(900))).isTrue();
        assertThat(service.owns(42L, "worker")).isTrue();
        assertThat(service.release(42L, "worker")).isTrue();
        verify(mapper).renewLease(42L, "worker", 900L);
        verify(mapper).hasValidLease(42L, "worker");
        verify(mapper).releaseLease(42L, "worker");
    }

    @Test
    void rejectsNonPositiveLease() {
        assertThatThrownBy(() -> service.tryAcquire(42L, "worker", Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapperClaimIsAtomicAndExcludesSuccessfulTasks() throws Exception {
        String sql = String.join(" ", LaMeloAgentDocumentTaskMapper.class
            .getMethod("tryAcquireLease", Long.class, String.class, long.class)
            .getAnnotation(Update.class).value());
        assertThat(sql).contains("task_status = 1", "task_status IN (2, 4)", "lease_until <= NOW()",
            "attempt_count = COALESCE(attempt_count, 0) + 1");
    }
}
