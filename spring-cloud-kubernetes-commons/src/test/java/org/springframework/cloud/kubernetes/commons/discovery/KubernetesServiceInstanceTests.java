/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesServiceInstanceTests {

	@Test
	void testFirstConstructor() {
		KubernetesServiceInstance instance = new KubernetesServiceInstance("instanceId","serviceId", "host", 8080,
			Map.of("k8s_namespace", "spring-k8s"), true);

		assertThat(instance.getInstanceId()).isEqualTo("instanceId");
		assertThat(instance.getServiceId()).isEqualTo("serviceId");
		assertThat(instance.getHost()).isEqualTo("host");
		assertThat(instance.getPort()).isEqualTo(8080);
		assertThat(instance.isSecure()).isTrue();
		assertThat(instance.getUri()).isEqualTo(URI.create("https://host:8080"));
		assertThat(instance.getMetadata()).isEqualTo(Map.of("k8s_namespace", "spring-k8s"));
		assertThat(instance.getScheme()).isEqualTo("https");
		assertThat(instance.getNamespace()).isEqualTo("spring-k8s");
		assertThat(instance.getCluster()).isNull();
	}

	@Test
	void testSecondConstructor() {
		KubernetesServiceInstance instance = new KubernetesServiceInstance("instanceId","serviceId", "host", 8080,
			Map.of("a", "b"), true, "spring-k8s", "cluster");

		assertThat(instance.getInstanceId()).isEqualTo("instanceId");
		assertThat(instance.getServiceId()).isEqualTo("serviceId");
		assertThat(instance.getHost()).isEqualTo("host");
		assertThat(instance.getPort()).isEqualTo(8080);
		assertThat(instance.isSecure()).isTrue();
		assertThat(instance.getUri()).isEqualTo(URI.create("https://host:8080"));
		assertThat(instance.getMetadata()).isEqualTo(Map.of("a", "b"));
		assertThat(instance.getScheme()).isEqualTo("https");
		assertThat(instance.getNamespace()).isEqualTo("spring-k8s");
		assertThat(instance.getCluster()).isEqualTo("cluster");
	}

}
