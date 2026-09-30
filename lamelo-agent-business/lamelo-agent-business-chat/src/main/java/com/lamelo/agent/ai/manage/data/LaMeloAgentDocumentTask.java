package com.lamelo.agent.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.lamelo.agent.database.data.BaseTableData;

import java.util.Date;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 数据实体
 * @author: 阿星不是程序员
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("lamelo_agent_document_task")
@EqualsAndHashCode(callSuper = true)
public class LaMeloAgentDocumentTask extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Long planId;

    private Integer taskType;

    private Integer taskStatus;

    private Integer currentStage;

    private Integer triggerSource;

    private String strategySnapshot;

    private Integer retryCount;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String leaseOwner;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Date leaseUntil;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer attemptCount;

    private Date startTime;

    private Date finishTime;

    private Long costMillis;

    private String errorCode;

    private String errorMsg;

    private String extJson;
}
