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

package org.apache.skywalking.apm.plugin.spring.ai.v1;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VectorStoreRetrieverInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        AbstractSpan span = ContextManager.createLocalSpan("Spring-ai/vectorStore/retrieve");
        span.setComponent(ComponentsDefine.SPRING_AI);
        SearchRequest searchRequest = (SearchRequest) allArguments[0];

        Tags.GEN_AI_VECTOR_STORE_TOP_K.set(span, String.valueOf(searchRequest.getTopK()));

        if (searchRequest.getFilterExpression() != null) {
            Tags.GEN_AI_VECTOR_STORE_FILTER_EXPRESSION.set(span, searchRequest.getFilterExpression().toString());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (!ContextManager.isActive()) {
            return ret;
        }

        if (ret != null) {
            List<Document> documents = (List<Document>) ret;
            AbstractSpan span = ContextManager.activeSpan();
            if (!documents.isEmpty()) {
                String recordIds = documents.stream()
                        .map(Document::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(","));

                if (!recordIds.isEmpty()) {
                    Tags.GEN_AI_VECTOR_STORE_RECORD_IDS.set(span, recordIds);
                }
            }
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
