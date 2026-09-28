package com.lamelo.agent.config;

import com.lamelo.agent.constant.LockInfoType;
import com.lamelo.agent.core.ManageLocker;
import com.lamelo.agent.lockinfo.LockInfoHandle;
import com.lamelo.agent.lockinfo.factory.LockInfoHandleFactory;
import com.lamelo.agent.lockinfo.impl.ServiceLockInfoHandle;
import com.lamelo.agent.servicelock.aspect.ServiceLockAspect;
import com.lamelo.agent.servicelock.factory.ServiceLockFactory;
import com.lamelo.agent.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 分布式锁 配置
 * @author: 阿星不是程序员
 **/
public class ServiceLockAutoConfiguration {

    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }

    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }

    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }

    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }

    @Bean
    public ServiceLockTool serviceLockUtil(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
