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

package org.apache.skywalking.apm.plugin.spring.ai.v1.common;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.plugin.spring.ai.v1.enums.AiProviderEnum;

import java.util.HashMap;
import java.util.Map;

public class ChatModelMetadataResolver {

    private static final ILog LOGGER = LogManager.getLogger(ChatModelMetadataResolver.class);

    private static final Map<String, ApiMetadata> MODEL_METADATA_MAP = new HashMap<>();

    static {
        for (AiProviderEnum provider : AiProviderEnum.values()) {
            if (provider.getModelClassName() != null && provider.getValue() != null) {
                MODEL_METADATA_MAP.put(
                        provider.getModelClassName(),
                        new ApiMetadata(provider.getValue())
                );
            }
        }
    }

    public static ApiMetadata getMetadata(Object chatModelInstance) {
        ApiMetadata metadata = MODEL_METADATA_MAP.get(chatModelInstance.getClass().getName());
        if (metadata == null) {
            return null;
        }

        return metadata;
    }

    public static ApiMetadata getMetadata(String modelClassName) {
        try {
            ApiMetadata metadata = MODEL_METADATA_MAP.get(modelClassName);
            if (metadata == null) {
                return null;
            }

            return metadata;
        } catch (Exception e) {
            LOGGER.error("spring-ai plugin get modelMetadata error: ", e);
            return null;
        }
    }

    public static class ApiMetadata {

        private final String providerName;
        private volatile String baseUrl;
        private volatile String completionsPath;

        ApiMetadata(String providerName) {
            this.providerName = providerName;
        }

        public String getProviderName() {
            return providerName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCompletionsPath() {
            return completionsPath;
        }

        public void setCompletionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
        }

        public String getPeer() {
            if (baseUrl != null && !baseUrl.isEmpty()) {
                return completionsPath != null && !completionsPath.isEmpty()
                        ? baseUrl + completionsPath
                        : baseUrl;
            }
            return providerName;
        }
    }
}
