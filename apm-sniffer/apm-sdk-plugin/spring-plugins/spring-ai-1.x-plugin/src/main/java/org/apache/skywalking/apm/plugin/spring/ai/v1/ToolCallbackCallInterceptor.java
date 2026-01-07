package org.apache.skywalking.apm.plugin.spring.ai.v1;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.lang.reflect.Method;

/**
 * @description:
 * @author: sym
 * @create: 2026-01-03 09:37
 **/

public class ToolCallbackCallInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        ToolCallback toolCallback = (ToolCallback) objInst;
        ToolDefinition definition = toolCallback.getToolDefinition();

        String toolName = definition != null ? definition.getName() : "unknown-tool";
        String toolInput = (String) allArguments[0];

        // 创建 LocalSpan 记录工具调用
        AbstractSpan span = ContextManager.createLocalSpan("SpringAI/Tool/" + toolName);
        span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION); // 建议自定义组件 ID

        // 记录输入参数
        Tags.ofKey("ai.tool.name").set(span, toolName);
        if (toolInput != null) {
            Tags.ofKey("ai.tool.input").set(span, toolInput);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            AbstractSpan span = ContextManager.activeSpan();
            if (ret instanceof String) {
                // 记录工具输出结果
                Tags.ofKey("ai.tool.output").set(span, (String) ret);
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t).errorOccurred();
        }
    }
}
