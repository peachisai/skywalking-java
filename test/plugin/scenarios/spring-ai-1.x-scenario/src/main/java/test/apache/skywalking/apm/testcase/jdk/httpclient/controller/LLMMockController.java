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
 */

package test.apache.skywalking.apm.testcase.jdk.httpclient.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/llm")
public class LLMMockController {

    @RequestMapping("/v1/chat/completions")
    public JSONObject completions(@RequestBody JSONObject request) {

        JSONArray messages = request.getJSONArray("messages");

        JSONObject lastMessage = messages.getJSONObject(messages.size() - 1);
        String lastRole = lastMessage.getString("role");

        String toolCallResponse = """
                {
                    "choices": [
                        {
                            "content_filter_results": {},
                            "finish_reason": "tool_calls",
                            "index": 0,
                            "logprobs": null,
                            "message": {
                                "annotations": [],
                                "content": null,
                                "refusal": null,
                                "role": "assistant",
                                "tool_calls": [
                                    {
                                        "function": {
                                            "arguments": "{\\"arg0\\":\\"new york\\"}",
                                            "name": "get_weather"
                                        },
                                        "id": "call_iV4bvFIZujbbVZwWbvsUKiiK",
                                        "type": "function"
                                    }
                                ]
                            }
                        }
                    ],
                    "created": 1768490813,
                    "id": "chatcmpl-CyJXJt7gxwDgz7UjYml95gc54784J",
                    "model": "gpt-4.1-2025-04-14",
                    "object": "chat.completion",
                    "prompt_filter_results": [
                        {
                            "prompt_index": 0,
                            "content_filter_results": {
                                "hate": { "filtered": false, "severity": "safe" },
                                "self_harm": { "filtered": false, "severity": "safe" },
                                "sexual": { "filtered": false, "severity": "safe" },
                                "violence": { "filtered": false, "severity": "safe" }
                            }
                        }
                    ],
                    "system_fingerprint": "fp_b9041e10b4",
                    "usage": {
                        "completion_tokens": 17,
                        "completion_tokens_details": {
                            "accepted_prediction_tokens": 0,
                            "audio_tokens": 0,
                            "reasoning_tokens": 0,
                            "rejected_prediction_tokens": 0
                        },
                        "prompt_tokens": 52,
                        "prompt_tokens_details": {
                            "audio_tokens": 0,
                            "cached_tokens": 0
                        },
                        "total_tokens": 69
                    }
                }
                """;

        String finalResponse = """
                       {
                           "choices": [
                               {
                                   "content_filter_results": {
                                       "hate": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "self_harm": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "sexual": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "violence": {
                                           "filtered": false,
                                           "severity": "safe"
                                       }
                                   },
                                   "finish_reason": "stop",
                                   "index": 0,
                                   "logprobs": null,
                                   "message": {
                                       "annotations": [],
                                       "content": "The weather in New York is currently sunny with a temperature of 10°C.",
                                       "refusal": null,
                                       "role": "assistant"
                                   }
                               }
                           ],
                           "created": 1768491057,
                           "id": "chatcmpl-CyJbFkZsyhOyHW2Otycoh2s4vrAZE",
                           "model": "gpt-4.1-2025-04-14",
                           "object": "chat.completion",
                           "prompt_filter_results": [
                               {
                                   "prompt_index": 0,
                                   "content_filter_results": {
                                       "hate": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "self_harm": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "sexual": {
                                           "filtered": false,
                                           "severity": "safe"
                                       },
                                       "violence": {
                                           "filtered": false,
                                           "severity": "safe"
                                       }
                                   }
                               }
                           ],
                           "system_fingerprint": "fp_b9041e10b4",
                           "usage": {
                               "completion_tokens": 17,
                               "completion_tokens_details": {
                                   "accepted_prediction_tokens": 0,
                                   "audio_tokens": 0,
                                   "reasoning_tokens": 0,
                                   "rejected_prediction_tokens": 0
                               },
                               "prompt_tokens": 83,
                               "prompt_tokens_details": {
                                   "audio_tokens": 0,
                                   "cached_tokens": 0
                               },
                               "total_tokens": 100
                           }
                       }         
                """;

        if ("tool".equals(lastRole)) {
            return JSON.parseObject(finalResponse);
        }

        return JSON.parseObject(toolCallResponse);
    }
}
