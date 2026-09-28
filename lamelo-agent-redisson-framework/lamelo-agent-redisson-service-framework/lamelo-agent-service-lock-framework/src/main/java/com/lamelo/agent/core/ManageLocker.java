package com.lamelo.agent.core;

import com.lamelo.agent.servicelock.LockType;
import com.lamelo.agent.servicelock.ServiceLocker;
import com.lamelo.agent.servicelock.impl.RedissonFairLocker;
import com.lamelo.agent.servicelock.impl.RedissonReadLocker;
import com.lamelo.agent.servicelock.impl.RedissonReentrantLocker;
import com.lamelo.agent.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static com.lamelo.agent.servicelock.LockType.Fair;
import static com.lamelo.agent.servicelock.LockType.Read;
import static com.lamelo.agent.servicelock.LockType.Reentrant;
import static com.lamelo.agent.servicelock.LockType.Write;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 分布式锁 锁缓存
 * @author: 阿星不是程序员
 **/
public class ManageLocker {

    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();

    public ManageLocker(RedissonClient redissonClient){
        cacheLocker.put(Reentrant,new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair,new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write,new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read,new RedissonReadLocker(redissonClient));
    }

    public ServiceLocker getReentrantLocker(){
        return cacheLocker.get(Reentrant);
    }

    public ServiceLocker getFairLocker(){
        return cacheLocker.get(Fair);
    }

    public ServiceLocker getWriteLocker(){
        return cacheLocker.get(Write);
    }

    public ServiceLocker getReadLocker(){
        return cacheLocker.get(Read);
    }
}
