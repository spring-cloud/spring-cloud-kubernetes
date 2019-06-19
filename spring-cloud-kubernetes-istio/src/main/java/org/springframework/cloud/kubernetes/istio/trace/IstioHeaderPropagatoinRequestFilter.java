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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.istio.IstioConstants;

/**
 * Inherited from HeaderPropagationRequestFilter and set the headers of Istio that need to be passed.
 *
 * @author wuzishu
 * @see org.springframework.cloud.kubernetes.istio.trace.HeaderPropagationRequestFilter
 */
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
public class IstioHeaderPropagatoinRequestFilter extends HeaderPropagationRequestFilter {

	private static List<String> HEADERS = Arrays.asList(IstioConstants.X_REQUEST_ID,
			IstioConstants.X_B3_TRACEID, IstioConstants.X_B3_SPANID,
			IstioConstants.X_B3_PARENTSPANID, IstioConstants.X_B3_SAMPLED,
			IstioConstants.X_B3_FLAGS, IstioConstants.X_OT_SPAN_CONTEXT,
			IstioConstants.USER_AGENT);

	/**
	 * Instantiates a new Istio header propagatoin request filter.
	 */
	public IstioHeaderPropagatoinRequestFilter() {
		super(HEADERS);
	}

}
