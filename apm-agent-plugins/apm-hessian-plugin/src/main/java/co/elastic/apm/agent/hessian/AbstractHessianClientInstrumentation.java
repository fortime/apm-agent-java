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
package co.elastic.apm.agent.hessian;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.caucho.hessian.client.HessianConnection;
import com.caucho.hessian.client.HessianProxy;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;

/**
 * Get hessian info before servlet instrumentation
 */
public abstract class AbstractHessianClientInstrumentation extends AbstractHessianInstrumentation {

    @VisibleForAdvice
    public static WeakConcurrentMap<Object, Span> spanMap = WeakMapSupplier.createMap();

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(AbstractHessianClientInstrumentation.class);

    @VisibleForAdvice
    public static final String EXTERNAL_TYPE = "external";

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public AbstractHessianClientInstrumentation(ElasticApmTracer tracer) {
        AbstractHessianClientInstrumentation.tracer = tracer;
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Hessian");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
//        HessianClientInterceptor
        return named("com.caucho.hessian.client.HessianProxy");
    }

    public static class HessianProxySendInstrumentation extends AbstractHessianClientInstrumentation {

        public HessianProxySendInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendRequest")
                .and(takesArguments(2))
                .and(takesArgument(0, named("java.lang.String")))
                .and(returns(hasSuperType(named("com.caucho.hessian.client.HessianConnection"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onSendRequest(
            @Advice.This HessianProxy thiz,
            @Advice.FieldValue("_type") Class<?> type,
            @Advice.Argument(0) String methodName) {
            logger.debug("enter");

            final ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
            if (tracer == null) {
                return;
            }

            AbstractSpan<?> traceContext = tracer.getActive();
            if (traceContext == null) {
                return;
            }
            Span span = traceContext.createExitSpan();
            if (span == null) {
                return;
            }

            span.withType(EXTERNAL_TYPE)
                .withSubtype(HESSIAN_SUBTYPE);
//        fillName(span, apiClass, methodName);
            URL url = thiz.getURL();

            if (url != null) {
                final Destination destination = span.getContext().getDestination();
                destination.getService()
                           .withType(EXTERNAL_TYPE)
                           .withName(HESSIAN_SUBTYPE);
                if (url.getAuthority() != null) {
                    destination.withAddressPort(url.getAuthority());
                    destination.getService().withResource(url.getAuthority());
                } else {
                    destination.withAddress(url.getHost()).withPort(url.getPort());
                    destination.getService().getResource().append(url.getHost()).append(':').append(
                        url.getPort());
                }
            }

            final String apiClassName = type != null ? type.getName() : null;
            if (apiClassName != null && !apiClassName.isEmpty()) {
                setName(span, apiClassName, methodName);
            }
            spanMap.put(thiz, span.activate());
        }
    }

    //
    public static class HessianProxyAndHeaderInstrumentation extends AbstractHessianClientInstrumentation {
        @VisibleForAdvice
        public static HelperClassManager<TextHeaderSetter<HessianConnection>> helperManager;

        public HessianProxyAndHeaderInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
            HessianProxyAndHeaderInstrumentation.init(tracer);
        }

        public static void init(ElasticApmTracer tracer) {
            helperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                                                                    "co.elastic.apm.agent.hessian.helper.HessianHeaderSetter");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("addRequestHeaders")
                .and(takesArguments(1))
                .and(takesArgument(0, hasSuperType(named("com.caucho.hessian.client.HessianConnection"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onAddRequestHeaders(@Advice.This HessianProxy thiz,
                                               @Advice.Argument(0) HessianConnection conn) {
            Span span = spanMap.get(thiz);
            if (span == null) {
                return;
            }
            TextHeaderSetter<HessianConnection> headerSetter = helperManager.getForClassLoaderOfClass(
                HessianConnection.class);
            if (headerSetter == null) {
                return;
            }
            span.propagateTraceContext(conn, headerSetter);
        }
    }

    public static class HessianProxyInvokeInstrumentation extends AbstractHessianClientInstrumentation {

        public HessianProxyInvokeInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("invoke")
                .and(takesArguments(3))
                .and(takesArgument(0, named("java.lang.Object")))
                .and(takesArgument(1, named("java.lang.reflect.Method")));
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This HessianProxy thiz) {
            Span span = spanMap.remove(thiz);
            if (span != null) {
                span.deactivate().end();
            }
        }
    }

}
