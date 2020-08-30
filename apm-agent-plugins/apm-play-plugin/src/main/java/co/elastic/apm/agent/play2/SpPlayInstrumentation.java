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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collection;
import play.api.mvc.RequestHeader;
import play.core.Router.Routes;
import play.router.RoutesCompiler;

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class SpPlayInstrumentation extends AbstractPlayInstrumentation {
    public static final Logger logger = LoggerFactory.getLogger(SpPlayInstrumentation.class);
    public static final WeakConcurrentMap<Object, String> pathPatternCache = WeakMapSupplier.createMap();

    @Nonnull
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> groupNames = super.getInstrumentationGroupNames();
        String[] newGroupNames = Arrays.copyOf(groupNames.toArray(new String[0]), groupNames.size() + 1);
        newGroupNames[groupNames.size()] = "sp-play";
        return Arrays.asList(newGroupNames);
    }

    public static class PathPatternInstrumentation extends SpPlayInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("play.router.DynamicRoutes");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("callAction");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        // public static void onEnter(@Advice.Argument(1) RequestHeader req, @Advice.Argument(2) Routes routes) {
        public static void onEnter(@Advice.Argument(2) RoutesCompiler.Route route,
                @Advice.Argument(3) Object refActionWrap,
                @Advice.Argument(4) Routes routes) {
            String pathPattern = pathPatternCache.get(refActionWrap);
            if (pathPattern == null) {
                String prefix = routes.prefix();
                pathPattern = String.format("%s%s%s", prefix, prefix.endsWith("/") ? "" : "/", route.path());
                pathPatternCache.put(refActionWrap, pathPattern);
            }
            logger.debug("The path pattern of action[{}]: {}", refActionWrap, pathPattern);
        }
    }

    public static class TransactionNameInstrumentation extends SpPlayInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("play.router.RefActionWrap");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("call");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        // public static void onEnter(@Advice.Argument(1) RequestHeader req, @Advice.Argument(2) Routes routes) {
        public static void onEnter(@Advice.This Object thiz,
                @Advice.Argument(1) RequestHeader req) {
            ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
            if (tracer == null) {
                return;
            }
            Transaction transaction = tracer.currentTransaction();
            if (transaction == null) {
                // ignore if there is no active transaction
                return;
            }
            String pathPattern = pathPatternCache.get(thiz);
            if (pathPattern == null) {
                // ignore if there is no path pattern
                return;
            }
            StringBuilder transactionName = transaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK);
            if (transactionName != null) {
                transactionName.append(req.method()).append(" ").append(pathPattern);
            }
        }
    }
}
