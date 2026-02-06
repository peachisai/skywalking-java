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
import org.apache.skywalking.apm.plugin.spring.ai.v1.config.SpringAiPluginConfig;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.lang.reflect.Method;

public class DefaultChatClientCallInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan("Spring-ai/chat-client/call");

        ChatClientRequest request = (ChatClientRequest) allArguments[0];
        Prompt prompt = request.prompt();
        ChatOptions chatOptions = prompt.getOptions();
        if (chatOptions == null) {
            return;
        }

        span.setComponent(ComponentsDefine.SPRING_AI);
        Tags.GEN_AI_REQUEST_MODEL.set(span, chatOptions.getModel());
        Tags.GEN_AI_TEMPERATURE.set(span, String.valueOf(chatOptions.getTemperature()));
        Tags.GEN_AI_TOP_K.set(span, String.valueOf(chatOptions.getTopK()));
        Tags.GEN_AI_TOP_P.set(span, String.valueOf(chatOptions.getTopP()));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (!ContextManager.isActive()) {
            return ret;
        }

        try {
            AbstractSpan span = ContextManager.activeSpan();
            ChatClientResponse response = (ChatClientResponse) ret;
            if (response == null || response.chatResponse() == null) {
                return ret;
            }

            ChatResponse chatResponse = response.chatResponse();
            ChatResponseMetadata metadata = chatResponse.getMetadata();

            long totalTokens = 0;

            if (metadata != null) {
                if (metadata.getId() != null) {
                    Tags.GEN_AI_RESPONSE_ID.set(span, metadata.getId());
                }
                if (metadata.getModel() != null) {
                    Tags.GEN_AI_RESPONSE_MODEL.set(span, metadata.getModel());
                }

                Usage usage = metadata.getUsage();
                if (usage != null) {
                    if (usage.getPromptTokens() != null) {
                        Tags.GEN_AI_USAGE_INPUT_TOKENS.set(span, String.valueOf(usage.getPromptTokens()));
                    }
                    if (usage.getCompletionTokens() != null) {
                        Tags.GEN_AI_USAGE_OUTPUT_TOKENS.set(span, String.valueOf(usage.getCompletionTokens()));
                    }
                    if (usage.getTotalTokens() != null) {
                        totalTokens = usage.getTotalTokens();
                        Tags.GEN_AI_USAGE_TOTAL_TOKENS.set(span, String.valueOf(totalTokens));
                    }
                }
            }

            Generation generation = chatResponse.getResult();
            if (generation != null && generation.getMetadata() != null) {
                String finishReason = generation.getMetadata().getFinishReason();
                if (finishReason != null) {
                    Tags.GEN_AI_RESPONSE_FINISH_REASONS.set(span, finishReason);
                }
            }

            collectContent(span, allArguments, generation, totalTokens);
        } finally {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }

    private void collectContent(AbstractSpan span, Object[] allArguments, Generation generation, long totalTokens) {
        int tokenThreshold = SpringAiPluginConfig.Plugin.SpringAi.CONTENT_COLLECT_THRESHOLD_TOKENS;

        if (tokenThreshold >= 0 && totalTokens < tokenThreshold) {
            return;
        }

        if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_PROMPT) {
            collectPrompt(span, allArguments);
        }

        if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_COMPLETION) {
            collectCompletion(span, generation);
        }
    }

    private void collectPrompt(AbstractSpan span, Object[] allArguments) {
        ChatClientRequest request = (ChatClientRequest) allArguments[0];
        if (request == null || request.prompt() == null) {
            return;
        }

        String promptText = request.prompt().getContents();
        if (promptText == null) {
            return;
        }

        int limit = SpringAiPluginConfig.Plugin.SpringAi.PROMPT_LENGTH_LIMIT;
        if (limit > 0 && promptText.length() > limit) {
            promptText = promptText.substring(0, limit);
        }
        Tags.GEN_AI_PROMPT.set(span, promptText);
    }

    private void collectCompletion(AbstractSpan span, Generation generation) {
        if (generation == null || generation.getOutput() == null) {
            return;
        }

        String completionText = generation.getOutput().getText();
        if (completionText == null) {
            return;
        }

        int limit = SpringAiPluginConfig.Plugin.SpringAi.COMPLETION_LENGTH_LIMIT;
        if (limit > 0 && completionText.length() > limit) {
            completionText = completionText.substring(0, limit);
        }
        Tags.GEN_AI_COMPLETION.set(span, completionText);
    }
}
