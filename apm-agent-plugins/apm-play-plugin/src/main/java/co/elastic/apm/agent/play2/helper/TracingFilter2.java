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

package co.elastic.apm.agent.play2.helper;

import static co.elastic.apm.agent.play2.helper.ServletRequestHeaderGetter.protocolOf;
import static co.elastic.apm.agent.play2.helper.ServletRequestHeaderGetter.queryStringOf;
import static co.elastic.apm.agent.play2.helper.ServletRequestHeaderGetter.schemaOf;
import static co.elastic.apm.agent.play2.helper.ServletRequestHeaderGetter.serverNameOf;
import static co.elastic.apm.agent.play2.helper.ServletRequestHeaderGetter.serverPortOf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import akka.stream.Materializer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.Transaction;
import play.api.routing.HandlerDef;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.Cookie;
import play.mvc.Result;
import play.routing.Router;

@Singleton
public class TracingFilter2 extends Filter {

    private static final String FRAMEWORK_NAME = "Play";
    private static final ServletTransactionHelper servletTransactionHelper;
    private static final ServletTransactionCreationHelper servletTransactionCreationHelper;

    static {
        servletTransactionHelper = new ServletTransactionHelper(GlobalTracer.requireTracerImpl());
        servletTransactionCreationHelper = new ServletTransactionCreationHelper(
            GlobalTracer.requireTracerImpl());
    }

    private final Pattern routePattern = Pattern.compile("\\$(\\w+)\\<\\[\\^/\\]\\+\\>", Pattern.DOTALL);

    @Inject
    public TracingFilter2(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> next,
                                         final Http.RequestHeader request) {
        final ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return next.apply(request);
        }

        HandlerDef def = null;
        try {
            def = request.attrs().get(Router.Attrs.HANDLER_DEF);
        } catch (Throwable t) {
            // ignore get HandlerDef exception
        }

        if (Objects.nonNull(def)) {
            Transaction transaction = null;
            // todo check need re-activate transactions for async requests
            if (tracer.isRunning()) {
                // todo
//                ServletContext servletContext = servletRequest.getServletContext();
//                if (servletContext != null) {
//                    // this makes sure service name discovery also works when attaching at runtime
//                    determineServiceName(servletContext.getServletContextName(),
//                                         servletContext.getClassLoader(), servletContext.getContextPath());
//                }

                transaction = servletTransactionCreationHelper.createAndActivateTransaction(request);

                if (transaction == null) {
                    // if the request is excluded, avoid matching all exclude patterns again on each filter invocation
//                    excluded.set(Boolean.TRUE);
                    return next.apply(request);
                }
                final Request req = transaction.getContext().getRequest();
                if (transaction.isSampled() && tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                    if (request.cookies() != null) {
                        for (Cookie cookie : request.cookies()) {
                            req.addCookie(cookie.name(), cookie.value());
                        }
                    }
                    final Map<String, List<String>> headers = request.getHeaders().toMap();
                    for (Entry<String, List<String>> header : headers.entrySet()) {
                        req.addHeader(header.getKey(), Collections.enumeration(header.getValue()));
                    }
                }
                transaction.setFrameworkName(FRAMEWORK_NAME);

                servletTransactionHelper.fillRequestContext(transaction, protocolOf(request),
                                                            request.method(), request.secure(),
                                                            schemaOf(request), serverNameOf(request),
                                                            serverPortOf(request), request.uri(),
                                                            queryStringOf(request),
                                                            request.remoteAddress(),
                                                            request.contentType().orElse(null));

                CompletionStage<Result> stage = next.apply(request);
                final Transaction transactionRef = transaction;
                return stage.thenApply(new Function<Result, Result>() {
                    @Override
                    public Result apply(Result result) {
                        transactionRef.activate();
                        if (transactionRef.isSampled() && tracer.getConfig(CoreConfiguration.class)
                                                                .isCaptureHeaders()) {
                            final Response resp = transactionRef.getContext().getResponse();
                            for (Entry<String, String> header : result.headers().entrySet()) {
                                resp.addHeader(header.getKey(), header.getValue());
                            }
                        }
                        final String contentTypeHeader = request.contentType().orElse(null);
                        final Map<String, String[]> parameterMap;
                        if (transactionRef.isSampled() && servletTransactionHelper.captureParameters(
                            request.method(), contentTypeHeader)) {
                            parameterMap = servletTransactionHelper.parametersOf(request);
                        } else {
                            parameterMap = null;
                        }

                        servletTransactionHelper.onAfter(transactionRef, null, true, result.status(),
                                                         false, request.method(), parameterMap, "",
                                                         request.path(), contentTypeHeader, true
                        );
                        return result;
                    }
                });

            }

        }
        return next.apply(request);
    }
}
