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
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package co.elastic.apm.agent.play2;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Return;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.play2.helper.FilterCreator;
import co.elastic.apm.agent.play2.helper.TracingFilter;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import play.api.inject.Injector;
import play.mvc.Filter;
import scala.collection.immutable.Seq;

public class HttpFiltersAdivce {
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(HttpFiltersAdivce.class);

    @Nullable
    @VisibleForAdvice
    public static Object injector;

    @Nullable
    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    @VisibleForAdvice
    public static HelperClassManager<TracingFilter> helperManager;
    @VisibleForAdvice
    public static HelperClassManager<FilterCreator> creatorHelperClassManager ;

    public static void init(ElasticApmTracer tracer) {
        HttpFiltersAdivce.tracer = tracer;
        helperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                                                                "co.elastic.apm.agent.play2.helper.TracingFilter");
        creatorHelperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                                                                "co.elastic.apm.agent.play2.helper.FilterCreator");

    }

    @Nonnull
    @AssignTo.Return
    @Advice.OnMethodExit(suppress = Throwable.class)
    private static Seq<Object> onExit(@Return Object ret) {
        logger.info("advice co.elastic.apm.agent.play2.HttpFiltersAdivce");
//        final TracingFilter filter = helperManager.getForClassLoaderOfClass(Http.RequestHeader.class);

        final TracingFilter filter = helperManager.getForClassLoaderOfClass(play.mvc.EssentialAction.class);
        if (filter == null) {
            return (Seq) ret;
        }
        final Seq seq = (Seq) ret;
        final List<Object> filters = new ArrayList<>(seq.size() + 1);
        filters.add(filter);
        filters.addAll(scala.collection.JavaConverters.asJavaCollection(seq));
        return scala.collection.JavaConverters.asScalaBuffer(filters).toList().toSeq();
    }

}
