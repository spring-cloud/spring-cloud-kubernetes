/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client;

import java.util.Collections;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;

/**
 * @author wind57
 */
final class StubProvider {

	static final String STUB_POD_IP = "127.0.0.1";

	static final String STUB_HOST_IP = "123.456.789.1";

	static final String STUB_NODE_NAME = "nodeName";

	static final String STUB_SERVICE_ACCOUNT = "serviceAccount";

	static final String STUB_POD_NAME = "mypod";

	static final String STUB_NAMESPACE = "default";

	static final Map<String, String> STUB_LABELS = Collections.singletonMap("spring", "cloud");

	private StubProvider() {
	}

	static V1Pod stubPod() {

		V1ObjectMeta metaData = new V1ObjectMeta().labels(STUB_LABELS).name(STUB_POD_NAME).namespace(STUB_NAMESPACE);
		V1PodStatus status = new V1PodStatus().podIP(STUB_POD_IP).hostIP(STUB_HOST_IP);
		V1PodSpec spec = new V1PodSpec().nodeName(STUB_NODE_NAME).serviceAccountName(STUB_SERVICE_ACCOUNT);

		return new V1Pod().metadata(metaData).status(status).spec(spec);
	}

}
