package com.lamelo.agent.ai.manage.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.service.DocumentStorageService;
import com.lamelo.agent.ai.manage.service.DocumentParserService;
import com.lamelo.agent.ai.manage.service.DocumentTaskLogService;
import com.lamelo.agent.ai.manage.support.DocumentAnalysisResult;
import com.lamelo.agent.enums.DocumentFileTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAsyncProcessLeaseTest {
    @Mock private LaMeloAgentDocumentMapper documentMapper;
    @Mock private LaMeloAgentDocumentTaskMapper taskMapper;
    @Mock private DocumentStorageService storageService;
    @Mock private DocumentParserService parserService;
    @Mock private DocumentTaskLogService taskLogService;
    @InjectMocks private DocumentAsyncProcessServiceImpl service;

    @Test
    void leaseColumnsAreNotWrittenByOrdinaryEntityUpdates() throws Exception {
        for (String name : new String[] {"leaseOwner", "leaseUntil", "attemptCount"}) {
            TableField annotation = LaMeloAgentDocumentTask.class.getDeclaredField(name)
                .getAnnotation(TableField.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.updateStrategy()).isEqualTo(FieldStrategy.NEVER);
        }
    }

    @Test
    void lostLeaseAtTaskWriteStopsBeforeDocumentOrStorageEffects() {
        when(documentMapper.selectById(11L)).thenReturn(new LaMeloAgentDocument());
        when(taskMapper.selectById(12L)).thenReturn(task());
        when(taskMapper.update(any(LaMeloAgentDocumentTask.class), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.handleParseRoute(11L, 12L, "owner-a"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("lease lost");

        verify(documentMapper, never()).updateById(any(LaMeloAgentDocument.class));
        verify(storageService, never()).downloadObject(any());
    }

    @Test
    void leaseLossAfterTaskWriteStopsBeforeStorageEffects() {
        when(documentMapper.selectById(11L)).thenReturn(new LaMeloAgentDocument());
        when(taskMapper.selectById(12L)).thenReturn(task());
        when(taskMapper.update(any(LaMeloAgentDocumentTask.class), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.hasValidLease(12L, "owner-a")).thenReturn(0);

        assertThatThrownBy(() -> service.handleParseRoute(11L, 12L, "owner-a"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("lease lost");

        verify(documentMapper, never()).updateById(any(LaMeloAgentDocument.class));
        verify(storageService, never()).downloadObject(any());
    }

    @Test
    void leaseLossAfterParsingStopsBeforeParsedTextUpload() {
        LaMeloAgentDocument document = new LaMeloAgentDocument();
        document.setObjectName("original.pdf");
        document.setOriginalFileName("original.pdf");
        document.setMimeType("application/pdf");
        document.setFileType(DocumentFileTypeEnum.PDF.getCode());
        when(documentMapper.selectById(11L)).thenReturn(document);
        when(taskMapper.selectById(12L)).thenReturn(task());
        when(taskMapper.update(any(LaMeloAgentDocumentTask.class), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.hasValidLease(12L, "owner-a")).thenReturn(1, 1, 1, 0);
        when(storageService.downloadObject("original.pdf")).thenReturn(new byte[] {1});
        when(parserService.parse(any(), any(), any(), any())).thenReturn(new DocumentAnalysisResult());

        assertThatThrownBy(() -> service.handleParseRoute(11L, 12L, "owner-a"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("lease lost");

        verify(parserService).parse(any(), any(), any(), any());
        verify(storageService, never()).uploadParsedText(any(), any());
    }

    private LaMeloAgentDocumentTask task() {
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setId(12L);
        task.setDocumentId(11L);
        return task;
    }
}
