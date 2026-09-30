package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentTaskLeaseService {
    private final LaMeloAgentDocumentTaskMapper taskMapper;

    public boolean tryAcquire(Long taskId, String owner, Duration lease) {
        return taskMapper.tryAcquireLease(taskId, owner, seconds(lease)) == 1;
    }

    public boolean renew(Long taskId, String owner, Duration lease) {
        return taskMapper.renewLease(taskId, owner, seconds(lease)) == 1;
    }

    public boolean release(Long taskId, String owner) {
        return taskMapper.releaseLease(taskId, owner) == 1;
    }

    public boolean owns(Long taskId, String owner) {
        return taskMapper.hasValidLease(taskId, owner) == 1;
    }

    private long seconds(Duration lease) {
        if (lease == null || lease.isNegative() || lease.isZero() || lease.getSeconds() < 1) {
            throw new IllegalArgumentException("Task lease must be at least one second");
        }
        return lease.getSeconds();
    }
}
