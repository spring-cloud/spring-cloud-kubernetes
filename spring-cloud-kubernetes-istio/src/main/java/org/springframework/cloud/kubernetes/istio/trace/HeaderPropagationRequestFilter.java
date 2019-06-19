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
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Define the RequestFilter and set the TraceHeader of Istio to the HeaderPropagationHolder,
 * which is provided to the thread pool and can be passed to the next call.
 *
 * @author wuzishu
 * @see org.springframework.web.filter.OncePerRequestFilter
 * @see org.springframework.cloud.kubernetes.istio.trace.HeaderPropagationHolder
 */

public abstract class HeaderPropagationRequestFilter extends OncePerRequestFilter {

	private final List<String> headersToPropagate;

	/**
	 * Instantiates a new Header propagation request filter.
	 * @param headersToPropagate the headers to propagate
	 */
	public HeaderPropagationRequestFilter(List<String> headersToPropagate) {
		this.headersToPropagate = headersToPropagate;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, FilterChain filterChain)
			throws ServletException, IOException {
		for (String header : headersToPropagate) {
			String value = httpServletRequest.getHeader(header);
			if (StringUtils.isNotBlank(value)) {
				HeaderPropagationHolder.put(header, value);
			}
		}
		filterChain.doFilter(httpServletRequest, httpServletResponse);
	}

}
