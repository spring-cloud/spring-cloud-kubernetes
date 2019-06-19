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

package org.springframework.cloud.kubernetes.istio;

/**
 * the IstioConstants description.
 *
 * @author wuzishu
 */
public final class IstioConstants {

	private IstioConstants() {
	}

	/**
	 * The constant X_REQUEST_ID.
	 */
	public static final String X_REQUEST_ID = "x-request-id";

	/**
	 * The constant X_B3_TRACEID.
	 */
	public static final String X_B3_TRACEID = "x-b3-traceid";

	/**
	 * The constant X_B3_SPANID.
	 */
	public static final String X_B3_SPANID = "x-b3-spanid";

	/**
	 * The constant X_B3_PARENTSPANID.
	 */
	public static final String X_B3_PARENTSPANID = "x-b3-parentspanid";

	/**
	 * The constant X_B3_SAMPLED.
	 */
	public static final String X_B3_SAMPLED = "x-b3-sampled";

	/**
	 * The constant X_B3_FLAGS.
	 */
	public static final String X_B3_FLAGS = "x-b3-flags";

	/**
	 * The constant X_OT_SPAN_CONTEXT.
	 */
	public static final String X_OT_SPAN_CONTEXT = "x-ot-span-context";

	/**
	 * The constant USER_AGENT.
	 */
	public static final String USER_AGENT = "user-agent";

}
