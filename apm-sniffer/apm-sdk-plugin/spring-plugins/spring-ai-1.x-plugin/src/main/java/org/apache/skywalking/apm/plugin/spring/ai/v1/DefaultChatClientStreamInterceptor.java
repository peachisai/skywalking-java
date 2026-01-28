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
import org.apache.skywalking.apm.plugin.spring.ai.v1.contant.Constants;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
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

public class DefaultChatClientStreamInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan("Spring-ai/stream");

        ChatClientRequest request = (ChatClientRequest) allArguments[0];
        if (request == null || request.prompt() == null) {
            return;
        }

        Prompt prompt = request.prompt();
        ChatOptions chatOptions = prompt.getOptions();
        if (chatOptions == null) {
            return;
        }

        span.setComponent(ComponentsDefine.SPRING_AI);
        Tags.GEN_AI_REQUEST_MODEL.set(span, chatOptions.getModel());
        Tags.GEN_AI_TEMPERATURE.set(span, String.valueOf(chatOptions.getTemperature()));

        ContextManager.getRuntimeContext().put(Constants.SPRING_AI_STREAM_START_TIME, System.currentTimeMillis());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (!ContextManager.isActive()) {
            return ret;
        }
        AbstractSpan span = ContextManager.activeSpan();
        span.prepareForAsync();

        ContextManager.stopSpan();

        Flux<ChatClientResponse> flux = (Flux<ChatClientResponse>) ret;

        AtomicReference<ChatResponse> lastResponseRef = new AtomicReference<>();

        StringBuilder completionBuilder = new StringBuilder();

        AtomicReference<String> finishReason = new AtomicReference<>("");

        AtomicBoolean firstResponseReceived = new AtomicBoolean(false);

        long startTime = (long) ContextManager.getRuntimeContext().get(Constants.SPRING_AI_STREAM_START_TIME);

        return flux.doOnNext(response -> {
                    if (response.chatResponse() != null) {

                        ChatResponse chatResponse = response.chatResponse();
                        lastResponseRef.set(chatResponse);

                        Generation generation = chatResponse.getResult();
                        if (generation == null) {
                            return;
                        }

                        if (generation.getOutput() != null && StringUtils.hasText(generation.getOutput().getText())) {
                            if (firstResponseReceived.compareAndSet(false, true)) {
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
                                    Tags.GEN_AI_USAGE_TOTAL_TOKENS.set(span, String.valueOf(usage.getTotalTokens().longValue()));
                                }

                                Tags.GEN_AI_RESPONSE_FINISH_REASONS.set(span, finishReason.get());

                                int tokenThreshold = SpringAiPluginConfig.Plugin.SpringAi.CONTENT_COLLECT_THRESHOLD_TOKENS;
                                if (tokenThreshold < 0 || totalTokens >= tokenThreshold) {

                                    if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_PROMPT) {
                                        ChatClientRequest request = (ChatClientRequest) allArguments[0];
                                        Prompt prompt = request.prompt();
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
                                        Tags.GEN_AI_COMPLETION.set(span, completionBuilder.toString());
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        span.log(t);
                    } finally {
                        span.asyncFinish();
                        ContextManager.getRuntimeContext().remove(Constants.SPRING_AI_STREAM_START_TIME);
                    }
                });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }

    private void collectContent(AbstractSpan span, StringBuilder completionBuilder, Object[] allArguments, long totalTokens) {
        int tokenThreshold = SpringAiPluginConfig.Plugin.SpringAi.CONTENT_COLLECT_THRESHOLD_TOKENS;

        if (tokenThreshold >= 0 && totalTokens < tokenThreshold) {
            return;
        }

        if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_PROMPT) {
            collectPrompt(span, allArguments);
        }

        if (SpringAiPluginConfig.Plugin.SpringAi.COLLECT_COMPLETION) {
            collectCompletion(span, completionBuilder);
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

    private void collectCompletion(AbstractSpan span, StringBuilder completionBuilder) {
        String completionText = completionBuilder.toString();
        if (completionText.isEmpty()) {
            return;
        }

        int limit = SpringAiPluginConfig.Plugin.SpringAi.COMPLETION_LENGTH_LIMIT;
        if (limit > 0 && completionText.length() > limit) {
            completionText = completionText.substring(0, limit);
        }
        Tags.GEN_AI_COMPLETION.set(span, completionText);
    }
}
