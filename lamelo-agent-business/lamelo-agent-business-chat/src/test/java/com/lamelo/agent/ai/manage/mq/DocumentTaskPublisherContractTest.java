package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentTaskPublisherContractTest {

    @Test
    void publishesParseAndIndexMessagesThroughBrokerNeutralContract() {
        DocumentTaskPublisher publisher = mock(DocumentTaskPublisher.class);
        DocumentParseRouteMessage parseMessage = new DocumentParseRouteMessage(11L, 22L);
        DocumentIndexBuildMessage indexMessage = new DocumentIndexBuildMessage(11L, 33L, 44L);

        publisher.sendParseRoute(parseMessage);
        publisher.sendIndexBuild(indexMessage);

        ArgumentCaptor<DocumentParseRouteMessage> parseCaptor = ArgumentCaptor.forClass(DocumentParseRouteMessage.class);
        ArgumentCaptor<DocumentIndexBuildMessage> indexCaptor = ArgumentCaptor.forClass(DocumentIndexBuildMessage.class);
        verify(publisher).sendParseRoute(parseCaptor.capture());
        verify(publisher).sendIndexBuild(indexCaptor.capture());

        assertThat(parseCaptor.getValue().getDocumentId()).isEqualTo(11L);
        assertThat(parseCaptor.getValue().getTaskId()).isEqualTo(22L);
        assertThat(indexCaptor.getValue().getDocumentId()).isEqualTo(11L);
        assertThat(indexCaptor.getValue().getTaskId()).isEqualTo(33L);
        assertThat(indexCaptor.getValue().getPlanId()).isEqualTo(44L);
    }
}
