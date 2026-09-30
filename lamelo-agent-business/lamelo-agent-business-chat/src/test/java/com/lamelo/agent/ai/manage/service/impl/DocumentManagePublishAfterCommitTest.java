package com.lamelo.agent.ai.manage.service.impl;

import com.baidu.fsg.uid.UidGenerator;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyPlan;
import com.lamelo.agent.ai.manage.dto.DocumentIndexBuildDto;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentStrategyPlanMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.mq.DocumentTaskPublisher;
import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.service.DocumentTaskLogService;
import com.lamelo.agent.enums.BusinessStatus;
import com.lamelo.agent.enums.DocumentParseStatusEnum;
import com.lamelo.agent.enums.DocumentStrategyStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentManagePublishAfterCommitTest {
    @Mock private LaMeloAgentDocumentMapper documentMapper;
    @Mock private LaMeloAgentDocumentStrategyPlanMapper planMapper;
    @Mock private LaMeloAgentDocumentTaskMapper taskMapper;
    @Mock private DocumentTaskPublisher taskPublisher;
    @Mock private DocumentTaskLogService taskLogService;
    @Mock private UidGenerator uidGenerator;
    @InjectMocks private DocumentManageServiceImpl service;

    @BeforeEach
    void openTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void closeTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void indexMessageIsPublishedOnlyAfterTransactionCommit() {
        LaMeloAgentDocument document = new LaMeloAgentDocument();
        document.setId(11L);
        document.setParseStatus(DocumentParseStatusEnum.PARSE_SUCCESS.getCode());
        document.setStrategyStatus(DocumentStrategyStatusEnum.CONFIRMED.getCode());
        document.setCurrentPlanId(13L);
        document.setStatus(BusinessStatus.YES.getCode());
        when(documentMapper.selectById(11L)).thenReturn(document);
        LaMeloAgentDocumentStrategyPlan plan = new LaMeloAgentDocumentStrategyPlan();
        plan.setStatus(BusinessStatus.YES.getCode());
        plan.setStrategySnapshot("1");
        when(planMapper.selectById(13L)).thenReturn(plan);
        when(uidGenerator.getUid()).thenReturn(12L);
        DocumentIndexBuildDto dto = new DocumentIndexBuildDto();
        dto.setDocumentId(11L);
        dto.setPlanId(13L);

        service.buildIndex(dto);

        verify(taskPublisher, never()).sendIndexBuild(any());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(taskPublisher).sendIndexBuild(new DocumentIndexBuildMessage(11L, 12L, 13L));
    }
}
