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
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.ai.v1.common.ChatModelMetadataResolver;
import org.apache.skywalking.apm.plugin.spring.ai.v1.config.SpringAiPluginConfig;
import org.apache.skywalking.apm.plugin.spring.ai.v1.contant.Constants;
import org.apache.skywalking.apm.plugin.spring.ai.v1.enums.AiProviderEnum;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatModelStreamInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ChatModelMetadataResolver.ApiMetadata apiMetadata = ChatModelMetadataResolver.getMetadata(objInst);
        String providerName = AiProviderEnum.UNKNOW.getValue();
        String peer = null;

        if (apiMetadata != null) {
            if (apiMetadata.getProviderName() != null) {
                providerName = apiMetadata.getProviderName();
            }
            peer = apiMetadata.getPeer();
        }
        AbstractSpan span = ContextManager.createExitSpan("Spring-ai/" + providerName + "/stream", peer);
        SpanLayer.asGenAI(span);

        span.setComponent(ComponentsDefine.SPRING_AI);
        SpanLayer.asGenAI(span);

        Prompt prompt = (Prompt) allArguments[0];
        if (prompt == null) {
            return;
        }

        ChatOptions chatOptions = prompt.getOptions();
        if (chatOptions == null) {
            return;
        }

        Tags.GEN_AI_OPERATION_NAME.set(span, Constants.CHAT);
        Tags.GEN_AI_REQUEST_MODEL.set(span, chatOptions.getModel());
        Tags.GEN_AI_TEMPERATURE.set(span, String.valueOf(chatOptions.getTemperature()));
        Tags.GEN_AI_TOP_K.set(span, String.valueOf(chatOptions.getTopK()));
        Tags.GEN_AI_TOP_P.set(span, String.valueOf(chatOptions.getTopP()));

        ContextManager.getRuntimeContext().put(Constants.SPRING_AI_STREAM_START_TIME, System.currentTimeMillis());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (!ContextManager.isActive()) {
            return ret;
        }
        AbstractSpan span = ContextManager.activeSpan();
        ContextSnapshot contextSnapshot = ContextManager.capture();

        span.prepareForAsync();
        ContextManager.stopSpan();

        Flux<ChatResponse> flux = (Flux<ChatResponse>) ret;

        AtomicReference<ChatResponse> lastResponseRef = new AtomicReference<>();

        final StringBuilder completionBuilder = new StringBuilder();

        AtomicReference<String> finishReason = new AtomicReference<>("");

        AtomicBoolean firstResponseReceived = new AtomicBoolean(false);

        Long startTime = (Long) ContextManager.getRuntimeContext().get(Constants.SPRING_AI_STREAM_START_TIME);
        ContextManager.getRuntimeContext().remove(Constants.SPRING_AI_STREAM_START_TIME);

        return flux.doOnNext(response -> {
                    if (response != null) {

                        lastResponseRef.set(response);

                        Generation generation = response.getResult();
                        if (generation == null) {
                            return;
                        }

                        if (generation.getOutput() != null && StringUtils.hasText(generation.getOutput().getText())) {
                            if (firstResponseReceived.compareAndSet(false, true) && startTime != null) {
                                Tags.GEN_AI_STREAM_TTFR.set(span, String.valueOf(System.currentTimeMillis() - startTime));
                            }
                        }

                        String reason = generation.getMetadata().getFinishReason();
                        if (reason != null) {
                            finishReason.set(reason);
                        }

                        if (generation.getOutput() != null && generation.getOutput().getText() != null) {
                            completionBuilder.append(generation.getOutput().getText());
                        }
                    }
                })
                .doOnError(span::log)
                .doFinally(signalType -> {
                    try {
                        ChatResponse finalResponse = lastResponseRef.get();

                        if (finalResponse != null) {
                            ChatResponseMetadata metadata = finalResponse.getMetadata();
                            if (metadata != null) {
                                if (metadata.getId() != null) {
                                    Tags.GEN_AI_RESPONSE_ID.set(span, metadata.getId());
                                }
                                if (metadata.getModel() != null) {
                                    Tags.GEN_AI_RESPONSE_MODEL.set(span, metadata.getModel());
                                }

                                Usage usage = metadata.getUsage();
                                long totalTokens = 0;
                                if (usage != null) {
                                    Tags.GEN_AI_USAGE_INPUT_TOKENS.set(span, String.valueOf(usage.getPromptTokens()));
                                    Tags.GEN_AI_USAGE_OUTPUT_TOKENS.set(span, String.valueOf(usage.getCompletionTokens()));
                                    totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                                    Tags.GEN_AI_CLIENT_TOKEN_USAGE.set(span, String.valueOf(usage.getTotalTokens().longValue()));
                                }

                                Tags.GEN_AI_RESPONSE_FINISH_REASONS.set(span, finishReason.get());

                                int tokenThreshold = SpringAiPluginConfig.Plugin.SpringAi.CONTENT_COLLECT_THRESHOLD_TOKENS;
                                if (tokenThreshold < 0 || totalTokens >= tokenThreshold) {

                                    if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_PROMPT) {
                                        Prompt prompt = (Prompt) allArguments[0];
                                        String promptText = prompt.getContents();
                                        if (promptText != null) {
                                            int limit = SpringAiPluginConfig.Plugin.SpringAi.PROMPT_LENGTH_LIMIT;
                                            if (limit > 0 && promptText.length() > limit) {
                                                promptText = promptText.substring(0, limit);
                                            }
                                            Tags.GEN_AI_PROMPT.set(span, promptText);
                                        }
                                    }

                                    if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_COMPLETION) {
                                        int limit = SpringAiPluginConfig.Plugin.SpringAi.COMPLETION_LENGTH_LIMIT;
                                        String output = completionBuilder.toString();
                                        if (limit > 0 && output.length() > limit) {
                                            output = output.substring(0, limit);
                                        }
                                        Tags.GEN_AI_COMPLETION.set(span, output);
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        span.log(t);
                    } finally {
                        span.asyncFinish();
                    }
                }).contextWrite(c -> c.put(Constants.SKYWALKING_CONTEXT_SNAPSHOT, contextSnapshot));
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
