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
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;

public class PlayFiltersCtorInstrumentation extends AbstractPlayInstrumentation {
    public static final Logger logger = LoggerFactory.getLogger(PlayFiltersCtorInstrumentation.class);
    private static final String ENHANCE_METHOD = "filters";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("play.api.http.EnabledFilters");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor().and(isAnnotatedWith(named("javax.inject.Inject")))
                              .and(takesArgument(2,
                                                 hasSuperType(named("play.api.inject.Injector"))));

    }

    @Override
    public Junction<ClassLoader> getClassLoaderMatcher() {
        // todo check it
        return classLoaderCanLoadClass("play.mvc.EssentialAction");
    }

    @Advice.OnMethodEnter()
    private static void onEnter(@Advice.Argument(2) Object injector) {
        logger.info("PlayFiltersCtorInstrumentation enter");
        HttpFiltersAdivce.injector = injector;
    }

}
