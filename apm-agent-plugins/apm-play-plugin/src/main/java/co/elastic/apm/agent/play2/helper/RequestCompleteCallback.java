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

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.mvc.SimpleResult;
import play.mvc.Http.HeaderNames;
import scala.Option;
import scala.collection.JavaConversions;
import scala.util.Failure;
import scala.util.Try;

public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {

    private static final Logger log = LoggerFactory.getLogger(RequestCompleteCallback.class);

    private final Transaction transaction;

    private final Request<?> request;
    private final TransactionHelper<Request<?>> servletTransactionHelper;

    public RequestCompleteCallback(final Transaction transaction,
                                   Request<?> request,
                                   TransactionHelper<Request<?>> servletTransactionHelper) {
        this.transaction = transaction;
        this.request = request;
        this.servletTransactionHelper = servletTransactionHelper;
    }

    @Override
    @Nullable
    public Object apply(final Try<Result> result) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        try {
            if (result.isFailure()) {
                Failure<?> failure = (Failure<?>) result;
                servletTransactionHelper.onAfter(transaction, failure.exception(), true, 500,
                                                 false, request.method(), null, "",
                                                 request.path(), null, true
                );
            } else {
                SimpleResult simpleResult = null;
                if (result.get() instanceof SimpleResult) {
                    simpleResult = (SimpleResult) result.get();
                }

                if (simpleResult != null && transaction.isSampled() &&
                    tracer.getConfig(CoreConfiguration.class).isCaptureHeaders()) {
                    final Response resp = transaction.getContext().getResponse();
                    Map<String, String> headers = JavaConversions.asJavaMap(
                        simpleResult.header().headers());
                    for (Entry<String, String> header : headers.entrySet()) {
                        resp.addHeader(header.getKey(), header.getValue());
                    }

                }
                final int status = simpleResult != null ? simpleResult.header().status() : 200;
                final String contentTypeHeader = simpleResult == null ? null : orNull(
                    simpleResult.header().headers().get(HeaderNames.CONTENT_TYPE));

                servletTransactionHelper.onAfter(updateSpanName(transaction, request), null, true, status,
                                                 false, request.method(), null, "",
                                                 request.path(), contentTypeHeader, true
                );
            }

        } catch (final Throwable t) {
            transaction.deactivate().end();
            log.info("error in play instrumentation", t);
        }
        return null;
    }

    public Transaction updateSpanName(final Transaction transaction, final play.api.mvc.Request<?> request) {
//        log.info("tags: {}",request.tags());
        Option<String> pathOption = request.tags().get("ROUTE_PATTERN");
        if (!pathOption.isEmpty()) {
            String path = pathOption.get();
            StringBuilder spanName = transaction.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
            if (spanName!=null) {
                spanName.append(request.method()).append(' ').append(path);
            }
            return transaction.withName(request.method() + " " + path, AbstractSpan.PRIO_METHOD_SIGNATURE);
        }
        return transaction;
    }

    @Nullable
    public static String orNull(Option<String> v) {
        return v.isDefined() ? v.get() : null;
    }
}
