/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.istio.trace;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Define the ClientHttpRequestInterceptor interceptor to pass the Istio request headers.
 *
 * @author wuzishu
 * @see org.springframework.cloud.kubernetes.istio.trace.HeaderPropagationHolder
 */
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
public class HeaderPropagationClientHttpRequestInterceptor
		implements ClientHttpRequestInterceptor {

	@Override
	public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] bytes,
			ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {
		HttpHeaders headers = httpRequest.getHeaders();
		for (Map.Entry<String, String> entry : HeaderPropagationHolder.entries()) {
			if (StringUtils.isNotBlank(entry.getKey())
					&& StringUtils.isNotBlank(entry.getValue())) {
				headers.set(entry.getKey(), entry.getValue());
			}
		}
		return clientHttpRequestExecution.execute(httpRequest, bytes);
	}

}
