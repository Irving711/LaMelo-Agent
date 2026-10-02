package com.lamelo.agent.ai.chatagent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationOwnerMapper {
    @Select("SELECT account_id FROM lamelo_agent_conversation_owner WHERE conversation_id = #{conversationId}")
    Long selectOwnerId(@Param("conversationId") String conversationId);

    @Insert("INSERT IGNORE INTO lamelo_agent_conversation_owner (conversation_id, account_id, create_time) "
        + "VALUES (#{conversationId}, #{accountId}, NOW())")
    int claim(@Param("conversationId") String conversationId, @Param("accountId") Long accountId);
}
