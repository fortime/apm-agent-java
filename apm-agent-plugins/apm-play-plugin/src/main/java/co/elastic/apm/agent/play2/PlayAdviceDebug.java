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
package co.elastic.apm.agent.play2;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;

public class PlayAdvice2 {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(PlayAdvice2.class);

    @VisibleForAdvice
    public static final String FRAMEWORK_NAME = "Play";

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    public static void log(String var1, Object... var2) {
        System.out.printf(var1 + "%n", var2);
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Transaction onEnter(@Advice.Argument(0) final Object request, @Advice.This Object thiz,
                                      @Advice.AllArguments Object[] allArgs) {
        log("Enter this: %s, %s ", thiz, thiz.getClass());
        StringBuilder sb = new StringBuilder();
        Class<?>[] interfaces = thiz.getClass().getInterfaces();
        for (Class<?> clazz : interfaces) {
            sb.append(clazz).append(',');
        }

        Class superClass = thiz.getClass().getSuperclass();
        log("Enter interfaces: %s,  %s", sb.toString(), superClass);
//        log("Enter req: %s, %s", request, request != null ? request.getClass() : "null");

        StringBuilder sb1 = new StringBuilder();
        for (Object arg : allArgs) {
            sb1.append(arg).append(',').append(arg.getClass()).append(" || ");
        }
        log("Enter args: %s ", sb1.toString());

        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTraceOnResponse(
        @Advice.This final Object thisAction,
        @Advice.Thrown final Throwable throwable,
        @Advice.Argument(0) final Object req) {

        logger.info("TT1 Exit");
    }

    // With this muzzle prevents this instrumentation from applying on Play 2.4+
}
