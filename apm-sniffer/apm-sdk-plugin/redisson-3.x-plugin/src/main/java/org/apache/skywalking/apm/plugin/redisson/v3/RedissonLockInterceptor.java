/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.redisson.api.RLock;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class RedissonLockInterceptor implements InstanceMethodsAroundInterceptor {

    private static final AbstractTag<String> TAG_LOCK_NAME = Tags.ofKey("lock_name");

    private static final AbstractTag<String> TAG_LEASE_TIME = Tags.ofKey("lease_time");

    private static final AbstractTag<String> TAG_THREAD_ID = Tags.ofKey("thead_id");

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan("Redisson/LOCK");
        span.setComponent(ComponentsDefine.REDISSON);
        SpanLayer.asCache(span);
        RLock rLock = (RLock) objInst;
        span.tag(TAG_LOCK_NAME, rLock.getName());
        Tags.CACHE_TYPE.set(span, "Redis");
        TimeUnit unit;
        if (allArguments[1] instanceof TimeUnit) {
            unit = (TimeUnit) allArguments[1];
            span.tag(TAG_LEASE_TIME, unit.toMillis((Long) allArguments[0]) + "ms");
            span.tag(TAG_THREAD_ID, String.valueOf(allArguments[2]));
        } else if (allArguments[2] instanceof TimeUnit) {
            unit = (TimeUnit) allArguments[2];
            span.tag(TAG_LEASE_TIME, unit.toMillis((Long) allArguments[1]) + "ms");
            span.tag(TAG_THREAD_ID, String.valueOf(allArguments[3]));
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
