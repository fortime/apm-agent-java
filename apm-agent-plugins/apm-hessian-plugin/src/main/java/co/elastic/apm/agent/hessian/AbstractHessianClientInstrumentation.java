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
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.caucho.hessian.client.HessianConnection;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;

/**
 * create exit span
 */
public abstract class AbstractHessianClientInstrumentation extends AbstractHessianInstrumentation {
    public static final WeakConcurrentMap<HessianConnection, Span> inFlightSpans = WeakMapSupplier.createMap();

    public static final Logger logger = LoggerFactory.getLogger(AbstractHessianClientInstrumentation.class);

    public static final String EXTERNAL_TYPE = "external";

    public static Tracer tracer = GlobalTracer.get();

    public static class CreateSpanInstrumentation extends AbstractHessianClientInstrumentation {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object enter(@Advice.This HessianConnection thiz,
                                   @Advice.Origin String signature) {
            if (tracer.getActive() == null) {
                return null;
            }

            Span span = inFlightSpans.get(thiz);
            logger.debug("enter: {}, {}", signature, span);
            boolean connected = thiz.getStatusCode() > 0;
            if (span == null && !connected) {
                span = tracer.getActive().createExitSpan();

                if (span != null) {
                    span.withType(EXTERNAL_TYPE)
                        .withSubtype(HESSIAN_SUBTYPE);
                    span.propagateTraceContext(thiz, HeaderSetter.instance());

                }
            }
            if (span != null) {
                span.activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void exit(@Advice.This HessianConnection thiz,
                                @Nullable @Advice.Thrown Throwable t,
                                @Nullable @Advice.Enter Object spanObject,
                                @Advice.Origin String signature) {
            Span span = (Span) spanObject;
            logger.debug("exit1: {},{}", signature, span);
            if (span == null) {
                return;
            }
            span.deactivate();
            int responseCode = thiz.getStatusCode();
            logger.debug("exit2 {},{}", signature, responseCode);
            if (responseCode > 0) {
                inFlightSpans.remove(thiz);
                // if the response code is set, the connection has been established via getOutputStream
                // if the response code is unset even after getOutputStream has been called, there will be an exception
                span.getContext().getHttp().withStatusCode(responseCode);
                span.captureException(t).end();
            } else if (t != null) {
                inFlightSpans.remove(thiz);
                span.captureException(t).end();
            } else {
                // if connect or getOutputStream has been called we can't end the span right away
                // we have to store associate it with thiz HttpURLConnection instance and end once getInputStream has been called
                // note that this could happen on another thread
                inFlightSpans.put(thiz, span);
            }
        }

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameContains("Connection").and(not(nameContains("Wrapper")));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("com.caucho.hessian.client.HessianConnection"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getOutputStream").and(takesArguments(0))
                                           .or(named("getInputStream").and(takesArguments(0)));
        }
    }

    // update span name
    public static class HessianProxySendInstrumentation extends AbstractHessianClientInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendRequest")
                .and(takesArguments(2))
                .and(takesArgument(0, named("java.lang.String")))
                .and(returns(hasSuperType(named("com.caucho.hessian.client.HessianConnection"))));
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void afterSendRequest(
            @Advice.FieldValue("_type") Class<?> type,
            @Advice.Argument(0) String methodName,
            @Nullable @Advice.Return(readOnly = true) HessianConnection hessianConnection
        ) {
            logger.debug("afterSendRequest1");
            if (hessianConnection == null) {
                return;
            }
            Span span = inFlightSpans.get(hessianConnection);

            logger.debug("afterSendRequest2: {}", span);
            if (span == null) {
                return;
            }

            final String apiClassName = type != null ? type.getName() : null;
            if (apiClassName != null && !apiClassName.isEmpty()) {
                setName(span, apiClassName, methodName);
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.caucho.hessian.client.HessianProxy");
    }

}
