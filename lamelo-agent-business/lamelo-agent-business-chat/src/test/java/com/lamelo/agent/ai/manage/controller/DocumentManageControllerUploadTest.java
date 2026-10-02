package com.lamelo.agent.ai.manage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.manage.dto.DocumentUploadDto;
import com.lamelo.agent.ai.manage.service.DocumentManageService;
import com.lamelo.agent.ai.manage.vo.DocumentUploadVo;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentManageControllerUploadTest {
    @Test
    void parsesJsonStringMetadataPartForMiniProgramUploads() {
        DocumentManageService service = mock(DocumentManageService.class);
        when(service.upload(any(), any())).thenReturn(new DocumentUploadVo());
        DocumentManageController controller = new DocumentManageController(service, new ObjectMapper());

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "body".getBytes());
        controller.upload(file, "{\"documentName\":\"doc\",\"operatorId\":\"7\"}");

        verify(service).upload(eq(file), any(DocumentUploadDto.class));
        org.mockito.ArgumentCaptor<DocumentUploadDto> captor = org.mockito.ArgumentCaptor.forClass(DocumentUploadDto.class);
        verify(service).upload(eq(file), captor.capture());
        assertEquals("doc", captor.getValue().getDocumentName());
        assertEquals("7", captor.getValue().getOperatorId());
    }

    @Test
    void rejectsMalformedJsonMetadataWithClientError() {
        DocumentManageService service = mock(DocumentManageService.class);
        DocumentManageController controller = new DocumentManageController(service, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "body".getBytes());

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> controller.upload(file, "{broken"));
        assertEquals(400, exception.getCode());
    }
}
