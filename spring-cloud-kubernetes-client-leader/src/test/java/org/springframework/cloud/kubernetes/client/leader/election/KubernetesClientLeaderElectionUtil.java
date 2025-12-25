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

package org.springframework.cloud.kubernetes.client.leader.election;

import java.time.OffsetDateTime;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1APIResourceList;
import io.kubernetes.client.openapi.models.V1APIResourceListBuilder;
import io.kubernetes.client.openapi.models.V1Lease;
import io.kubernetes.client.openapi.models.V1LeaseBuilder;
import io.kubernetes.client.openapi.models.V1LeaseSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

/**
 * @author wind57
 */
final class KubernetesClientLeaderElectionUtil {

	static final String HOLDER_IDENTITY = "leader";

	private KubernetesClientLeaderElectionUtil() {

	}

	static WireMockServer wireMockServer() {
		WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());
		return wireMockServer;
	}

	static ApiClient apiClientWithLeaseSupport(WireMockServer wireMockServer) {

		// lease lock is supported
		V1APIResourceList leaseList = new V1APIResourceListBuilder()
			.addToResources(new V1APIResource().kind("Lease").name("my-lease").namespaced(false).singularName("Lease"))
			.withApiVersion("v1")
			.withGroupVersion("v1")
			.withKind("Foo")
			.build();
		stubFor(get("/apis/coordination.k8s.io/v1")
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(leaseList))));

		// lease that is requested
		V1Lease lease = new V1LeaseBuilder().withKind("Lease")
			.withSpec(new V1LeaseSpecBuilder().withLeaseTransitions(1)
				.withAcquireTime(OffsetDateTime.now())
				.withLeaseDurationSeconds(2)
				.withRenewTime(OffsetDateTime.now())
				.withHolderIdentity(HOLDER_IDENTITY)
				.build())
			.build();
		stubFor(get("/apis/coordination.k8s.io/v1/namespaces/default/leases/spring-k8s-leader-election-lock")
			.willReturn(aResponse().withStatus(200).withBody(JSON.serialize(lease))));

		return new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
	}

	@Configuration
	static class ApiClientConfiguration {

		@Bean
		@Primary
		ApiClient apiClient() {
			return apiClientWithLeaseSupport(wireMockServer());
		}

	}

}
