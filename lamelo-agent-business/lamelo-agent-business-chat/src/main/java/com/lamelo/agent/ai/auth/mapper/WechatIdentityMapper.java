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
    /**
     * 原地改挂微信身份到另一个平台账号。
     * 不能先逻辑删除再插入：active_openid / active_unionid 唯一索引会冲突。
     */
    int updateUserIdById(@Param("id") Long id, @Param("userId") Long userId);
    int logicalDeleteByUserId(@Param("userId") Long userId);
}
