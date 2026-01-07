package org.apache.skywalking.apm.plugin.spring.ai.v1.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.HierarchyMatch;

/**
 * @description:
 * @author: sym
 * @create: 2026-01-03 09:36
 **/

public class ToolCallbackInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    private static final String ENHANCE_INTERFACE = "org.springframework.ai.tool.ToolCallback";
    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.spring.ai.ToolCallbackCallInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        // 对应 implementsInterface(named(...))
        return HierarchyMatch.byHierarchyMatch(ENHANCE_INTERFACE);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("call")
                                .and(takesArguments(2))
                                .and(takesArgument(0, named("java.lang.String")))
                                // 这里 OTel 代码里还匹配了返回值为 String
                                .and(returns(named("java.lang.String")));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
}
