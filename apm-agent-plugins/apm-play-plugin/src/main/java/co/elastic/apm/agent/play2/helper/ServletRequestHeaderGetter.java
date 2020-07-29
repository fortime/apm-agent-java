/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.play2.helper;

import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;

class ServletRequestHeaderGetter implements TextHeaderGetter<RequestHeader> {

    private static final ServletRequestHeaderGetter INSTANCE = new ServletRequestHeaderGetter();

    static ServletRequestHeaderGetter getInstance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Http.RequestHeader request) {
        return request.getHeaders().get(headerName).orElse(null);
    }

    @Override
    public <S> void forEach(String headerName, Http.RequestHeader request, S state,
                            HeaderConsumer<String, S> consumer) {
        List<String> headers = request.getHeaders().getAll(headerName);
        for (String headerValue : headers) {
            consumer.accept(headerValue, state);
        }
    }

    public static String schemaOf(Http.RequestHeader request) {
        return request.header("scheme").orElse("http");
    }

    public static String serverNameOf(Http.RequestHeader request) {
        String[] hostPort = request.host().split(":");
        return hostPort[0];
    }

    public static int serverPortOf(Http.RequestHeader request) {
        String[] hostPort = request.host().split(":");
        if (hostPort.length > 1) {
            return Integer.parseInt(hostPort[hostPort.length - 1]);
        } else {
            return 0;
        }
    }

    public static String queryStringOf(Http.RequestHeader request) {
        StringBuilder sb = new StringBuilder();
        boolean first = false;
        for (Entry<String, String[]> query : request.queryString().entrySet()) {
            for (String v : query.getValue()) {
                if (!first) {
                    sb.append('&');
                }
                first = true;
                sb.append(query.getKey()).append('=').append(v);
            }
        }
        return sb.toString();
    }

    public static String protocolOf(Http.RequestHeader request) {
        // todo
        return "HTTP/1.1";
    }

    public static String serverPathOf(Http.RequestHeader request) {
        // todo
        return "";
    }

    public static String userAgentOf(Http.RequestHeader request) {
        return request.header("User-Agent").orElse("");
    }

}
