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
package co.elastic.apm.agent.spring.webmvc.template;


import co.elastic.apm.agent.spring.webmvc.template.groovy.GroovyConfiguration;
import co.elastic.apm.agent.spring.webmvc.template.groovy.GroovyController;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ContextConfiguration(classes = {GroovyController.class, GroovyConfiguration.class})
class GroovyTemplateTest extends AbstractViewRenderingInstrumentationTest {

    @Test
    void testViewRender() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/groovy"));

        MvcResult mvcResult = resultActions.andReturn();

        verifySpanCapture("GroovyMarkup", " hello", mvcResult.getResponse(), "<!DOCTYPE html><html lang='en'><head><meta http-equiv='\"Content-Type\" content=\"text/html; charset=utf-8\"'/><title>My page</title></head><body><h2>A Groovy View with Spring MVC</h2><div>msg: Message 123</div></body></html>");
    }
}
