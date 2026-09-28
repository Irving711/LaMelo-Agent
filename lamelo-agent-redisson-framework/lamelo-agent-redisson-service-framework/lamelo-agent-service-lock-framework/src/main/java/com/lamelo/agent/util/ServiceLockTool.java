package com.lamelo.agent.util;

import com.lamelo.agent.constant.LockInfoType;
import com.lamelo.agent.lockinfo.LockInfoHandle;
import com.lamelo.agent.lockinfo.factory.LockInfoHandleFactory;
import com.lamelo.agent.servicelock.LockType;
import com.lamelo.agent.servicelock.ServiceLocker;
import com.lamelo.agent.servicelock.factory.ServiceLockFactory;
import com.lamelo.agent.servicelock.info.LockTimeOutStrategy;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 分布式锁 方法类型操作
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class ServiceLockTool {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;

    public void execute(TaskRun taskRun,String name,String [] keys) {
        execute(taskRun,name,keys,20);
    }

    public void execute(TaskRun taskRun,String name,String [] keys,long waitTime){
        execute(LockType.Reentrant,taskRun,name,keys,waitTime);
    }

    public void execute(LockType lockType,TaskRun taskRun,String name,String [] keys) {
        execute(lockType,taskRun,name,keys,20);
    }

    public void execute(LockType lockType,TaskRun taskRun,String name,String [] keys,long waitTime) {
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name,keys);
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, waitTime);
        if (result) {
            try {
                taskRun.run();
            }finally {
                lock.unlock(lockName);
            }
        }else {
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
    }

    public <T> T submit(TaskCall<T> taskCall,String name,String [] keys){
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name,keys);
        ServiceLocker lock = serviceLockFactory.getLock(LockType.Reentrant);
        boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 30);
        if (result) {
            try {
                return taskCall.call();
            }finally {
                lock.unlock(lockName);
            }
        }else {
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
        return null;
    }

    public RLock getLock(LockType lockType, String name, String [] keys) {
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name,keys);
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }

    public RLock getLock(LockType lockType, String lockName) {
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }
}
