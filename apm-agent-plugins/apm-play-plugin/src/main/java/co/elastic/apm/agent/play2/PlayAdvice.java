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
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.play2.helper.TransactionHelper;
import play.api.mvc.Action;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.concurrent.Future;

@VisibleForAdvice
public class PlayAdvice {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(PlayAdvice.class);

    @VisibleForAdvice
    public static final String FRAMEWORK_NAME = "Play";

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Transaction onEnter(@Advice.Argument(0) final Request<?> request) {
        final ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        if (!tracer.isRunning()) {
            return null;
        }

        final TransactionHelper<Request<?>> transactionHelper =
            PlayInstrumentation.transactionHelperClassManager.getForClassLoaderOfClass(
                Request.class);

        if (transactionHelper == null) {
            return null;
        }
        final Transaction transaction = transactionHelper.createAndActivateTransaction(request);
        if (transaction == null) {
            return null;
        }

        transactionHelper.fillTransactionName(transaction, request);
        transactionHelper.fillRequestContext(transaction, request);
        transaction.setFrameworkName(FRAMEWORK_NAME);
        return transaction;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTraceOnResponse(
        @Advice.Enter final Transaction transaction,
        @Advice.This final Object thisAction,
        @Advice.Thrown final Throwable throwable,
        @Advice.Argument(0) final Request<?> req,
        @Advice.Return(readOnly = false) final Future<Result> responseFuture) {

        final TransactionHelper<Request<?>> transactionHelper =
            PlayInstrumentation.transactionHelperClassManager.getForClassLoaderOfClass(
                Request.class);
        if (transaction == null || transactionHelper == null) {
            return;
        }
        if (throwable == null) {
            responseFuture.onComplete(
                transactionHelper.createRequestCompleteCallback(transaction, req),
                ((Action<?>) thisAction).executionContext());
        } else {
            transactionHelper.onAfter(transaction, throwable, true, 500, true,
                                      req.method(), false);
        }
        transaction.deactivate();
    }

}
