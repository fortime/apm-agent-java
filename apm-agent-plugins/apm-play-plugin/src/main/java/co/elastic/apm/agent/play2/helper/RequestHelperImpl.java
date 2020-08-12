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

import static co.elastic.apm.agent.play2.helper.Utils.orElse;

public class RequestHelperImpl implements RequestHelper<play.api.mvc.Request<?>> {
    @Override
    public String schemaOf(play.api.mvc.Request<?> request) {
        return orElse(request.headers().get("scheme"), "http");
    }

    @Override
    public boolean secure(play.api.mvc.Request<?> request) {
        return schemaOf(request).contentEquals("https");
    }

    @Override
    public String serverNameOf(play.api.mvc.Request<?> request) {
        String[] hostPort = request.host().split(":");
        return hostPort[0];
    }

    @Override
    public int serverPortOf(play.api.mvc.Request<?> request) {
        String[] hostPort = request.host().split(":");
        if (hostPort.length > 1) {
            return Integer.parseInt(hostPort[hostPort.length - 1]);
        } else {
            return 0;
        }
    }

    @Override
    public String queryStringOf(play.api.mvc.Request<?> request) {
        return request.rawQueryString();
    }

    @Override
    public String protocolOf(play.api.mvc.Request<?> request) {
        // todo
        return "HTTP/1.1";
    }

    @Override
    public String userAgentOf(play.api.mvc.Request<?> request) {
        return orElse(request.headers().get("User-Agent"), "");
    }
}
