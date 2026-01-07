package org.apache.skywalking.apm.plugin.spring.ai.v1;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import javax.management.Query;
import java.lang.reflect.Method;

public class DocumentRetrieverInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Query query = (Query) allArguments[0];

        // 创建 LocalSpan 记录检索过程
        AbstractSpan span = ContextManager.createLocalSpan("SpringAI/DocumentRetriever/retrieve");

        // 设置组件 ID (建议在 ComponentsDefine 中定义专用的 SPRING_AI ID)
        span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);

        if (query != null) {
            // 记录原始查询文本
            Tags.ofKey("ai.retrieve.query").set(span, query.getText());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            AbstractSpan span = ContextManager.activeSpan();
            if (ret instanceof List) {
                List<?> documents = (List<?>) ret;
                // 记录检索到的文档数量
                Tags.ofKey("ai.retrieve.document_count").set(span, String.valueOf(documents.size()));

                // 可选：记录前几个文档的 ID 或部分内容以便追踪
                if (!documents.isEmpty() && documents.get(0) instanceof Document) {
                    Document firstDoc = (Document) documents.get(0);
                    Tags.ofKey("ai.retrieve.first_doc_id").set(span, firstDoc.getId());
                }
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
