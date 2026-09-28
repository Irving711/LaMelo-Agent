package com.lamelo.agent.ai.manage.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;
import com.lamelo.agent.core.SpringUtil;
import com.lamelo.agent.enums.DocumentManageCode;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 消息组件
 * @author: 阿星不是程序员
 **/

@AllArgsConstructor
@Component
public class DocumentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final DocumentManageProperties properties;

    public void sendParseRoute(DocumentParseRouteMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getParseTopic(), String.valueOf(message.getDocumentId()), message);
    }

    public void sendIndexBuild(DocumentIndexBuildMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getIndexTopic(), String.valueOf(message.getDocumentId()), message);
    }

    private void send(String topic, String key, Object message) {
        try {

            String payload = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, key, payload).get();
        } catch (Exception exception) {
            throw new LaMeloAgentFrameException(DocumentManageCode.KAFKA_SEND_FAILED.getCode(),
                "Kafka 消息发送失败: " + exception.getMessage(), exception);
        }
    }
}
