package com.lamelo.agent.ai.auth.mapper;

import com.lamelo.agent.ai.auth.data.WechatIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WechatIdentityMapper {
    WechatIdentity selectActiveByOpenid(@Param("openid") String openid);
    WechatIdentity selectActiveByUnionid(@Param("unionid") String unionid);
    WechatIdentity selectActiveByUserId(@Param("userId") Long userId);
    int insertIdentity(WechatIdentity identity);
    int logicalDeleteByUserId(@Param("userId") Long userId);
}
