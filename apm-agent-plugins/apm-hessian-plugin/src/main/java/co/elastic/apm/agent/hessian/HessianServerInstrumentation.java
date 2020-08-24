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
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.server.HessianSkeleton;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Transaction;

/**
 * span should be created by servlet instrumentation, update span name here
 */
public class HessianServerInstrumentation extends AbstractHessianInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(HessianServerInstrumentation.class);

    @VisibleForAdvice
    public static Tracer tracer = GlobalTracer.get();

    public HessianServerInstrumentation() {
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.caucho.hessian.server.HessianSkeleton");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
//        public void invoke(Object service,
//            AbstractHessianInput in,
//            AbstractHessianOutput out)
        return named("invoke")
            .and(takesArguments(3))
            .and(takesArgument(1, hasSuperType(named("com.caucho.hessian.io.AbstractHessianInput"))))
            .and(takesArgument(2, hasSuperType(named("com.caucho.hessian.io.AbstractHessianOutput"))));
    }



    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This final HessianSkeleton thiz,
                              @Advice.Argument(1) final AbstractHessianInput in) {
//        logger.debug("HessianServerInstrumentation01");
        if (!tracer.isRunning()) {
            return;
        }

        final Transaction transaction = tracer.currentTransaction();
        if (transaction == null) {
            return;
        }
        transaction.setFrameworkName(FRAMEWORK_NAME);
        final String method = in.getMethod();
        final String apiClassName = thiz.getAPIClassName();
        if (apiClassName != null && !apiClassName.isEmpty()) {
            setName(transaction,apiClassName,method);
        }
    }

}
