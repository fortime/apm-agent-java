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

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.play2.helper.RequestHelper;
import co.elastic.apm.agent.play2.helper.TransactionHelper;
import co.elastic.apm.agent.play2.helper.TransactionCreationHelper;
import play.api.mvc.Request;

public class PlayInstrumentation extends AbstractPlayInstrumentation {
    //    private static final Logger logger = LoggerFactory.getLogger(PlayFiltersCtorInstrumentation.class);

    @VisibleForAdvice
    public static HelperClassManager<TransactionCreationHelper> creatioHelperClassManager;

    // ok to use Request as type erase..
    @VisibleForAdvice
    public static HelperClassManager<TransactionHelper<Request<?>>> transactionHelperClassManager;


    private final ElasticApmTracer tracer;

    public PlayInstrumentation(ElasticApmTracer tracer) {
        this.tracer = tracer;
        PlayInstrumentation.init(tracer);
    }

    private synchronized static void init(ElasticApmTracer tracer) {
        creatioHelperClassManager =
            HelperClassManager.ForAnyClassLoader.of(tracer,
                                                    "co.elastic.apm.agent.play2.helper.TransactionCreationHelperImpl",
                                                    "co.elastic.apm.agent.play2.helper.Utils",
                                                    "co.elastic.apm.agent.play2.helper.RequestHelperImpl",
                                                    "co.elastic.apm.agent.play2.helper.RequestHeaderGetter");

        transactionHelperClassManager =
            HelperClassManager.ForAnyClassLoader.of(tracer,
                                                    "co.elastic.apm.agent.play2.helper.TransactionHelperImpl",
                                                    "co.elastic.apm.agent.play2.helper.Utils",
                                                    "co.elastic.apm.agent.play2.helper.RequestHeaderGetter",
                                                    "co.elastic.apm.agent.play2.helper.RequestHelperImpl",
                                                    "co.elastic.apm.agent.play2.helper.RequestCompleteCallback");
    }

//    @Override
//    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
//        return nameContains("play").and(nameContains("Action"));
//    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("play.api.mvc.Action"));
//        return nameStartsWith("play").and(nameContains("Action")).and(not(nameContains("Annotations")));
//        return nameStartsWith("play.core").and(nameContains("Action"))
//                                          .or(nameStartsWith("play.api").and(nameContains("Action")));
//        return hasSuperType(named("play.core.j.JavaAction"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("apply")
            .and(takesArgument(0, named("play.api.mvc.Request")))
            .and(returns(named("scala.concurrent.Future")));

//        return named("apply");
    }

    @Override
    public Junction<ClassLoader> getClassLoaderMatcher() {
//        TODO WHY NOT?
//        return classLoaderCanLoadClass("play.api.mvc.Action");
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("play.api.mvc.Action"));
//        return any();
    }

    @Override
    public Class<?> getAdviceClass() {
        return PlayAdvice.class;
    }
}
