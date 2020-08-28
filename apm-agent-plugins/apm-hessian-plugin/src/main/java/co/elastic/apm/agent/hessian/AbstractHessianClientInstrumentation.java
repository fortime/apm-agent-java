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
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.caucho.hessian.client.HessianConnection;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;

/**
 * create exit span.
 * invoke chain:
 * {@literal
 *   -->invoke
 *        --> sendRequest  #create span
 *          -->addRequestHeaders #propagate
 *   <--(invoke exit)  #end span
 * }
 *
 */
public abstract class AbstractHessianClientInstrumentation extends AbstractHessianInstrumentation {

    public static final GlobalThreadLocal<Object> inFlightSpans = GlobalThreadLocal.get(
        AbstractHessianClientInstrumentation.class, "inFlightSpans");

    public static final Logger logger = LoggerFactory.getLogger(AbstractHessianClientInstrumentation.class);

    public static final String EXTERNAL_TYPE = "external";

    public static Tracer tracer = GlobalTracer.get();

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.caucho.hessian.client.HessianProxy");
    }

    public static class CreateSpanInstrumentation extends AbstractHessianClientInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendRequest")
                .and(takesArguments(2))
                .and(takesArgument(0, named("java.lang.String")))
                .and(returns(hasSuperType(named("com.caucho.hessian.client.HessianConnection"))));
        }

        @OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(
            @Advice.FieldValue("_type") Class<?> type,
            @Advice.Argument(0) String methodName
        ) {

            if (tracer.getActive() == null) {
                return;
            }

            Span span = tracer.getActive().createExitSpan();
            if (span != null) {
                inFlightSpans.set(span);
                span.withType(EXTERNAL_TYPE)
                    .withSubtype(HESSIAN_SUBTYPE);

                final String apiClassName = type != null ? type.getName() : null;
                if (apiClassName != null && !apiClassName.isEmpty()) {
                    setName(span, apiClassName, methodName);
                }
                span.activate();
            }
        }

    }

    public static class EndSpanInstrumentation extends AbstractHessianClientInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("invoke")
                .and(takesArguments(3))
                .and(takesArgument(0, named("java.lang.Object")))
                .and(takesArgument(1, named("java.lang.reflect.Method")));
        }

        @OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void afterInvoke(@Nullable @Advice.Thrown Throwable t) {
            Span span = (Span) inFlightSpans.getAndRemove();
            if (span != null) {
                span.captureException(t);
                span.captureException(t).deactivate().end();
            }
        }

    }

    /**
     * propagateTraceContext.
     * need to add before actual send request, so can not be set when  HessianProxy#sendRequest exited
     */
    public static class PropagateTraceContextInstrumentation extends AbstractHessianClientInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("addRequestHeaders")
                .and(takesArguments(1))
                .and(takesArgument(0, hasSuperType(named("com.caucho.hessian.client.HessianConnection"))));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onAddRequestHeaders(@Advice.Argument(0) HessianConnection conn) {
            if (tracer.getActive() == null) {
                return;
            }

            Span span = (Span) inFlightSpans.get();
            if (span == null) {
                return;
            }
            span.propagateTraceContext(conn, HeaderSetter.instance());
        }
    }

}
