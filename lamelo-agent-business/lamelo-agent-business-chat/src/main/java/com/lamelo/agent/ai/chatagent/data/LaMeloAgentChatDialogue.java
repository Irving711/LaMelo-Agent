package com.lamelo.agent.ai.chatagent.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.lamelo.agent.database.data.BaseTableData;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 数据实体
 * @author: 阿星不是程序员
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("lamelo_agent_chat_dialogue")
@EqualsAndHashCode(callSuper = true)
public class LaMeloAgentChatDialogue extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("dialogue_code")
    private String conversationId;

    @TableField("dialogue_stage")
    private Integer sessionStatus;

    @TableField("chat_mode")
    private Integer chatMode;

    @TableField("selected_document_id")
    private Long selectedDocumentId;

    @TableField("selected_document_name")
    private String selectedDocumentName;
}
