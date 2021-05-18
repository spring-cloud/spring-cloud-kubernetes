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

import io.kubernetes.client.openapi.models.V1Pod;
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
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_HOST_IP;
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_NAMESPACE;
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_NODE_NAME;
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_POD_IP;
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_POD_NAME;
import static org.springframework.cloud.kubernetes.client.StubProvider.STUB_SERVICE_ACCOUNT;

/**
 * @author Ryan Baxter
 */
@ExtendWith(MockitoExtension.class)
class KubernetesClientInfoContributorTests {

	@Mock
	private PodUtils<V1Pod> utils;

	@Test
	void getDetailsIsNotInside() {
		when(utils.currentPod()).thenReturn(() -> null);
		KubernetesClientInfoContributor infoContributor = new KubernetesClientInfoContributor(utils);
		Map<String, Object> details = infoContributor.getDetails();

		assertThat(details.size()).isEqualTo(1);
		assertThat(details.get(INSIDE)).isEqualTo(false);
	}

	@Test
	void getDetailsInside() {

		when(utils.currentPod()).thenReturn(StubProvider::stubPod);
		KubernetesClientInfoContributor infoContributor = new KubernetesClientInfoContributor(utils);
		Map<String, Object> details = infoContributor.getDetails();

		assertThat(details.size()).isEqualTo(7);
		assertThat(details.get(INSIDE)).isEqualTo(true);

		assertThat(details.get(HOST_IP)).isEqualTo(STUB_HOST_IP);
		assertThat(details.get(POD_IP)).isEqualTo(STUB_POD_IP);
		assertThat(details.get(NODE_NAME)).isEqualTo(STUB_NODE_NAME);
		assertThat(details.get(SERVICE_ACCOUNT)).isEqualTo(STUB_SERVICE_ACCOUNT);
		assertThat(details.get(POD_NAME)).isEqualTo(STUB_POD_NAME);
		assertThat(details.get(NAMESPACE)).isEqualTo(STUB_NAMESPACE);
	}

}
