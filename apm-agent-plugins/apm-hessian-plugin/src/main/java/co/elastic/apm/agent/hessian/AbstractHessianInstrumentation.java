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

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.not;

import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import net.bytebuddy.matcher.ElementMatcher.Junction;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;

public abstract class AbstractHessianInstrumentation extends TracerAwareInstrumentation {
    public static final String FRAMEWORK_NAME = "hessian";
    public static final String HESSIAN_SUBTYPE = "hessian";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("hessian", "experimental1");
    }

    @Override
    public Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(
            classLoaderCanLoadClass("com.caucho.hessian.HessianUnshared"));
    }

    public static void setName(AbstractSpan<?> span, String className, @Nullable String methodName) {

        final StringBuilder name = span.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK);
        if (name != null) {
            name.append(className, className.lastIndexOf('.') + 1, className.length());
            if (methodName != null) {
                name.append('#').append(methodName);
            }
        }
    }
}
