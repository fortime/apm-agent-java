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
package co.elastic.apm.agent.play.ws;

import java.util.List;

import javax.annotation.Nullable;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import play.api.libs.ws.WS.WSRequest;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Seq;

@SuppressWarnings("unused")
public class RequestHeaderAccessor implements TextHeaderGetter<WSRequest>, TextHeaderSetter<WSRequest> {
    @Override
    public void setHeader(String headerName, String headerValue, WSRequest request) {
        request.setHeader(headerName, headerValue);
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, WSRequest request) {
        Option<String> header = request.header(headerName);
        if (!header.isEmpty()) {
            return header.get();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, WSRequest carrier, S state,
                            HeaderConsumer<String, S> consumer) {
        final Option<Seq<String>> headers = carrier.allHeaders().get(headerName);
        if (headers.isDefined()) {
            final List<String> headerList = JavaConversions.asJavaList(headers.get());
            for (String header : headerList) {
                consumer.accept(header, state);
            }
        }
    }
}
