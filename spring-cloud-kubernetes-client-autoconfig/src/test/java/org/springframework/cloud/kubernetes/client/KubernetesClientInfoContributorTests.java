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

import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.kubernetes.commons.PodUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.HOST_IP;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.INSIDE;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.NAMESPACE;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.NODE_NAME;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.POD_IP;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.POD_NAME;
import static org.springframework.cloud.kubernetes.client.KubernetesClientHealthIndicator.SERVICE_ACCOUNT;

/**
 * @author Ryan Baxter
 */
@ExtendWith(MockitoExtension.class)
class KubernetesClientInfoContributorTests {

	private static final String STUB_POD_IP = "127.0.0.1";

	private static final String STUB_HOST_IP = "123.456.789.1";

	private static final String STUB_NODE_NAME = "nodeName";

	private static final String STUB_SERVICE_ACCOUNT = "serviceAccount";

	private static final String STUB_POD_NAME = "mypod";

	private static final String STUB_NAMESPACE = "default";

	@Mock
	private PodUtils<V1Pod> utils;

	@Test
	void getDetailsIsNotInside() {
		when(utils.currentPod()).thenReturn(() -> null);
		KubernetesClientInfoContributor infoContributor = new KubernetesClientInfoContributor(utils);
		Map<String, Object> details = infoContributor.getDetails();

		assertThat(details.containsKey(INSIDE)).isTrue();
		assertThat(details.get(INSIDE)).isEqualTo(false);
	}

	@Test
	void getDetailsInside() {

		when(utils.currentPod()).thenReturn(this::stubPod);
		KubernetesClientInfoContributor infoContributor = new KubernetesClientInfoContributor(utils);
		Map<String, Object> details = infoContributor.getDetails();

		assertThat(details.containsKey(INSIDE)).isTrue();
		assertThat(details.get(INSIDE)).isEqualTo(true);

		assertThat(details.get(HOST_IP)).isEqualTo(STUB_HOST_IP);
		assertThat(details.get(POD_IP)).isEqualTo(STUB_POD_IP);
		assertThat(details.get(NODE_NAME)).isEqualTo(STUB_NODE_NAME);
		assertThat(details.get(SERVICE_ACCOUNT)).isEqualTo(STUB_SERVICE_ACCOUNT);
		assertThat(details.get(POD_NAME)).isEqualTo(STUB_POD_NAME);
		assertThat(details.get(NAMESPACE)).isEqualTo(STUB_NAMESPACE);
	}

	private V1Pod stubPod() {

		V1ObjectMeta metaData = new V1ObjectMeta().name(STUB_POD_NAME).namespace(STUB_NAMESPACE);
		V1PodStatus status = new V1PodStatus().podIP(STUB_POD_IP).hostIP(STUB_HOST_IP);
		V1PodSpec spec = new V1PodSpec().nodeName(STUB_NODE_NAME).serviceAccountName(STUB_SERVICE_ACCOUNT);

		return new V1Pod().metadata(metaData).status(status).spec(spec);
	}

}
