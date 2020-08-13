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

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.asm.Advice.Thrown;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import play.api.libs.ws.Response;
import play.api.libs.ws.WS.WSRequest;
import play.libs.F;
import play.libs.F.Callback;
import play.libs.F.Function;
import play.libs.F.Promise;

public class PlayWSInstrumentation extends TracerAwareInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(PlayWSInstrumentation.class);

    public static ElasticApmTracer tracer = null;

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderGetter<WSRequest>> headerGetterHelperClassManager;
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<WSRequest>> headerSetterHelperClassManager;

    public PlayWSInstrumentation(ElasticApmTracer tracer) {
        PlayWSInstrumentation.init(tracer);
    }

    public static void init(ElasticApmTracer trace) {
        PlayWSInstrumentation.tracer = trace;

        synchronized (PlayWSInstrumentation.class) {
            if (headerGetterHelperClassManager == null) {
                headerGetterHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                                                                                         "co.elastic.apm.agent.play.ws.RequestHeaderAccessor"
                );
                headerSetterHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                                                                                         "co.elastic.apm.agent.play.ws.RequestHeaderAccessor"
                );
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("play", "play-ws");
    }

    @Override
    public Junction<ClassLoader> getClassLoaderMatcher() {
        // todo find simple interface
        return any();
//        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("play.libs.WS.WSRequest"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
//        play.libs.WS.WSRequest#execute
        return named("play.libs.WS$WSRequest");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(0))
            .and(returns(named("play.libs.F$Promise")));
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static Span onEnter(
        @Advice.This WSRequest request
    ) {
        if (tracer == null) {
            return null;
        }

        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }
        Span span = parent.createExitSpan();
        if (span == null) {
            return null;
        }
        String urlStr = request.url();
        URI uri = null;
        try {
            URI url = new URI(urlStr);
        } catch (Exception e) {
            // ignore
        }
        span = HttpClientHelper.startHttpClientSpan(parent, request.method(),
                                                    uri,
                                                    null);
        TextHeaderSetter<WSRequest>
            headerSetter = headerSetterHelperClassManager.getForClassLoaderOfClass(HttpRequest.class);
        TextHeaderGetter<WSRequest>
            headerGetter = headerGetterHelperClassManager.getForClassLoaderOfClass(HttpRequest.class);
        if (span != null) {
            span.activate();
            if (headerSetter != null) {
                span.propagateTraceContext(request, headerSetter);
            }

        } else if (headerGetter != null && !TraceContext.containsTraceContextTextHeaders(request, headerGetter)
                   && headerSetter != null) {
            // re-adds the header on redirects
            parent.propagateTraceContext(request, headerSetter);
        }

        // todo handle error

        return span;
    }

    @OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Thrown Throwable thrown,
                               @Nullable @Advice.Enter Span span,
                               @Advice.Return(readOnly = true) Promise<Response> responsePromise
    ) {
        logger.debug("exit");
        if (span == null) {
            return;
        }
        if (span != null) {
            final Span spanRef = span;
            responsePromise.map(new Function<Response, Response>() {
                @Override
                public Response apply(Response response) throws Throwable {
                    if (response != null) {
                        spanRef.getContext().getHttp().withStatusCode(response.status());
                    }
                    spanRef.end();
                    return response;
                }
            }).onFailure(new Callback<Throwable>() {
                @Override
                public void invoke(Throwable throwable) throws Throwable {
                    spanRef.captureException(throwable);
                    throw throwable;
                }
            });
        }

        span.captureException(thrown);
        span.deactivate();
    }

}
