package com.lamelo.agent.config;

import com.lamelo.agent.constant.LockInfoType;
import com.lamelo.agent.handle.RedissonDataHandle;
import com.lamelo.agent.locallock.LocalLockCache;
import com.lamelo.agent.lockinfo.LockInfoHandle;
import com.lamelo.agent.lockinfo.factory.LockInfoHandleFactory;
import com.lamelo.agent.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.lamelo.agent.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.lamelo.agent.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 防重复幂等配置
 * @author: 阿星不是程序员
 **/
public class RepeatExecuteLimitAutoConfiguration {

    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }

    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
