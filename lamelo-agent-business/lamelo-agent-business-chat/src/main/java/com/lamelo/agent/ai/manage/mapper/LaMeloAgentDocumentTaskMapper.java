package com.lamelo.agent.ai.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: Mapper层
 * @author: 阿星不是程序员
 **/

@Mapper
public interface LaMeloAgentDocumentTaskMapper extends BaseMapper<LaMeloAgentDocumentTask> {

    @Update("UPDATE lamelo_agent_document_task SET lease_owner = #{owner}, " +
        "lease_until = DATE_ADD(NOW(), INTERVAL #{seconds} SECOND), " +
        "attempt_count = COALESCE(attempt_count, 0) + 1 " +
        "WHERE id = #{taskId} AND (task_status = 1 OR task_status IN (2, 4)) " +
        "AND (lease_until IS NULL OR lease_until <= NOW())")
    int tryAcquireLease(@Param("taskId") Long taskId, @Param("owner") String owner,
                        @Param("seconds") long seconds);

    @Update("UPDATE lamelo_agent_document_task SET " +
        "lease_until = DATE_ADD(NOW(), INTERVAL #{seconds} SECOND) " +
        "WHERE id = #{taskId} AND lease_owner = #{owner} " +
        "AND task_status = 2 AND lease_until > NOW()")
    int renewLease(@Param("taskId") Long taskId, @Param("owner") String owner,
                   @Param("seconds") long seconds);

    @Update("UPDATE lamelo_agent_document_task SET lease_owner = NULL, lease_until = NULL " +
        "WHERE id = #{taskId} AND lease_owner = #{owner}")
    int releaseLease(@Param("taskId") Long taskId, @Param("owner") String owner);

    @Select("SELECT COUNT(*) FROM lamelo_agent_document_task WHERE id = #{taskId} " +
        "AND lease_owner = #{owner} AND lease_until > NOW()")
    int hasValidLease(@Param("taskId") Long taskId, @Param("owner") String owner);
}
