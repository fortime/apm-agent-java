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
package co.elastic.apm.agent.report;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dslplatform.json.JsonWriter;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;

public class LogReporter implements Reporter {
    private static final Logger logger = LoggerFactory.getLogger(LogReporter.class);
    private final DslJsonSerializer jsonSerializer;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong reported = new AtomicLong();

    public LogReporter(DslJsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    @Override
    public void start() {

    }


    @Override
    public void report(Transaction transaction) {
        if (logger.isWarnEnabled()) {
            logger.warn(jsonSerializer.toJsonString(transaction));
            reported.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
        transaction.decrementReferences();
    }

    @Override
    public void report(Span span) {
        if (logger.isWarnEnabled()) {
            logger.warn(jsonSerializer.toJsonString(span));
            reported.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
        span.decrementReferences();
    }

    @Override
    public void report(ErrorCapture error) {
        if (logger.isWarnEnabled()) {
            logger.warn(jsonSerializer.toJsonString(error));
            reported.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
        error.recycle();
    }

    @Override
    public void report(JsonWriter jsonWriter) {
        if (jsonWriter.size() == 0) {
            return;
        }
        if (logger.isWarnEnabled()) {
            logger.warn(jsonWriter.toString());
            reported.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
    }

    @Override
    public long getDropped() {
        return dropped.get();
    }

    @Override
    public long getReported() {
        return reported.get();
    }

    @Override
    public Future<Void> flush() {
        return new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                return null;
            }
        });
    }

    @Override
    public void close() {

    }
}
