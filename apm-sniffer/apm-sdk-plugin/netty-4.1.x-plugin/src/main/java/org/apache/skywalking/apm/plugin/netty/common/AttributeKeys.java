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

package org.apache.skywalking.apm.plugin.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.util.AttributeKey;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import java.util.HashMap;
import java.util.Map;

public class AttributeKeys {

    public static final AttributeKey<Map<ChannelHandler, ChannelHandler>> HANDLER_CLASS_MAP = AttributeKey.valueOf("sw8_handler_class_map");

    public static final AttributeKey<AbstractSpan> HTTP_CLIENT_SPAN = AttributeKey.valueOf("sw8_http_client_span");

    public static final AttributeKey<AbstractSpan> HTTP_SERVER_SPAN = AttributeKey.valueOf("sw8_http_server_span");

    public static Map<ChannelHandler, ChannelHandler> getOrCreateHandlerMap(Channel channel) {
        Map<ChannelHandler, ChannelHandler> map = channel.attr(HANDLER_CLASS_MAP).get();
        if (map == null) {
            map = new HashMap<>();
            channel.attr(HANDLER_CLASS_MAP).set(map);
        }

        return map;
    }
}
