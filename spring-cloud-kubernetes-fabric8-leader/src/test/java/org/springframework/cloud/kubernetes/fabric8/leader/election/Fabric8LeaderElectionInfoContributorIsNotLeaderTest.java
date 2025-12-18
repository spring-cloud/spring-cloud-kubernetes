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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.APIGroupList;
import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.kubernetes.commons.leader.LeaderUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=info",
				"management.endpoint.info.show-details=always", "management.info.kubernetes.enabled=true",
				"spring.cloud.kubernetes.leader.election.enabled=true" })
@AutoConfigureWebTestClient
class Fabric8LeaderElectionInfoContributorIsNotLeaderTest {

	private static final String HOLDER_IDENTITY = "leader";

	@LocalManagementPort
	private int port;

	@Autowired
	private WebTestClient webClient;

	private static MockedStatic<LeaderUtils> leaderUtilsMockedStatic;

	@BeforeAll
	static void beforeAll() {
		leaderUtilsMockedStatic = Mockito.mockStatic(LeaderUtils.class);
		leaderUtilsMockedStatic.when(LeaderUtils::hostName).thenReturn("non-" + HOLDER_IDENTITY);
	}

	@AfterAll
	static void afterAll() {
		leaderUtilsMockedStatic.close();
	}

	@Test
	void infoEndpointIsNotLeaderTest() {
		webClient.get()
			.uri("http://localhost:{port}/actuator/info", port)
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("leaderElection.isLeader")
			.isEqualTo(false)
			.jsonPath("leaderElection.leaderId")
			.isEqualTo("non-" + HOLDER_IDENTITY);
	}

	@TestConfiguration
	static class Configuration {

		@Bean
		@Primary
		KubernetesClient mockKubernetesClient() {
			KubernetesClient client = Mockito.mock(KubernetesClient.class);
			mockForLeaseSupport(client);
			mockForLeaderSupport(client);
			return client;
		}

		private void mockForLeaseSupport(KubernetesClient client) {
			Mockito.when(client.getApiResources("coordination.k8s.io/v1"))
				.thenReturn(
						new APIResourceListBuilder().withResources(new APIResourceBuilder().withKind("Lease").build())
							.build());

			APIGroupList apiGroupList = new APIGroupListBuilder().addNewGroup()
				.withVersions(new GroupVersionForDiscoveryBuilder().withGroupVersion("coordination.k8s.io/v1").build())
				.endGroup()
				.build();

			Mockito.when(client.getApiGroups()).thenReturn(apiGroupList);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private void mockForLeaderSupport(KubernetesClient client) {

			Lease lease = new LeaseBuilder().withNewMetadata()
				.withName("spring-k8s-leader-election-lock")
				.endMetadata()
				.withSpec(new LeaseSpecBuilder().withHolderIdentity(HOLDER_IDENTITY)
					.withLeaseDurationSeconds(1)
					.withAcquireTime(ZonedDateTime.now())
					.withRenewTime(ZonedDateTime.now())
					.withLeaseTransitions(1)
					.build())
				.build();

			MixedOperation mixedOperation = Mockito.mock(MixedOperation.class);
			Mockito.when(client.resources(Lease.class)).thenReturn(mixedOperation);

			Resource resource = Mockito.mock(Resource.class);
			Mockito.when(resource.get()).thenReturn(lease);

			NonNamespaceOperation nonNamespaceOperation = Mockito.mock(NonNamespaceOperation.class);
			Mockito.when(mixedOperation.inNamespace("default")).thenReturn(nonNamespaceOperation);
			Mockito.when(nonNamespaceOperation.withName("spring-k8s-leader-election-lock")).thenReturn(resource);

		}

	}

}
