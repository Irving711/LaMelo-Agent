package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;

/**
 * Broker-neutral publisher for document processing tasks.
 */
public interface DocumentTaskPublisher {

    void sendParseRoute(DocumentParseRouteMessage message);

    void sendIndexBuild(DocumentIndexBuildMessage message);
}
