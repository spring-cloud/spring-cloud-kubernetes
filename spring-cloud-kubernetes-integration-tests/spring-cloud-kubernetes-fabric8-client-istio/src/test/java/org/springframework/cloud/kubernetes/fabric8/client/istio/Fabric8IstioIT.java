/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.istio;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.Fabric8ClientIntegrationTest;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
@Fabric8ClientIntegrationTest(namespaces = { "istio-test", "istio-system" },
		withImages = "spring-cloud-kubernetes-fabric8-client-istio", deployIstio = true)
class Fabric8IstioIT {

	@Test
	void test() {
		WebClient client = builder().baseUrl("http://localhost:32321/profiles").build();

		@SuppressWarnings("unchecked")
		List<String> result = client.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(List.class)
			.retryWhen(retrySpec())
			.block();

		// istio profile is present
		Assertions.assertThat(result).contains("istio");
	}

}
