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
package co.elastic.apm.agent.play2.helper;

import java.util.Map;

import javax.annotation.Nullable;

import co.elastic.apm.agent.impl.transaction.Transaction;

public interface TransactionHelper<RequestT> {

    /**
     * OK as type erase
     */
    RequestHelper<RequestT> requestHelper();

    // create transaction
    @Nullable
    Transaction createAndActivateTransaction(RequestT request);

    void fillRequestContext(Transaction transaction, RequestT request);

    void fillRequestContext(Transaction transaction, String protocol, String method, boolean secure,
                            String scheme, String serverName, int serverPort, String requestURI,
                            String queryString,
                            String remoteAddr, @Nullable String contentTypeHeader);

    void onAfter(Transaction transaction, @Nullable Throwable exception, boolean committed, int status,
                 boolean overrideStatusCodeOnThrowable, String method,
                 boolean deactivate);

    void onAfter(Transaction transaction, @Nullable Throwable exception, boolean committed, int status,
                 boolean overrideStatusCodeOnThrowable, String method,
                 @Nullable Map<String, String[]> parameterMap,
                 @Nullable String servletPath, @Nullable String pathInfo, @Nullable String contentTypeHeader,
                 boolean deactivate);

    RequestCompleteCallback createRequestCompleteCallback(Transaction transaction,
                                                          RequestT request);

    //    other helper
    void fillTransactionName(Transaction transaction, RequestT request);
}
