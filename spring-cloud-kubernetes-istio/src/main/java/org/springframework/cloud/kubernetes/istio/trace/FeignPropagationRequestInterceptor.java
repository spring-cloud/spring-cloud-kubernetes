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

import java.util.Arrays;
import java.util.List;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.istio.IstioConstants;

/**
 * the FeignPropagationRequestInterceptor description.
 *
 * @author wuzishu
 */
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
public class FeignPropagationRequestInterceptor implements RequestInterceptor {

	private static final Logger LOG = LoggerFactory
			.getLogger(FeignPropagationRequestInterceptor.class);

	private static List<String> HEADERS = Arrays.asList(IstioConstants.X_REQUEST_ID,
			IstioConstants.X_B3_TRACEID, IstioConstants.X_B3_SPANID,
			IstioConstants.X_B3_PARENTSPANID, IstioConstants.X_B3_SAMPLED,
			IstioConstants.X_B3_FLAGS, IstioConstants.X_OT_SPAN_CONTEXT,
			IstioConstants.USER_AGENT);

	@Override
	public void apply(RequestTemplate requestTemplate) {
		for (String header : HEADERS) {
			String value = HeaderPropagationHolder.get(header);
			if (StringUtils.isNotBlank(value)) {
				requestTemplate.header(header, value);
			}
		}
	}

}
