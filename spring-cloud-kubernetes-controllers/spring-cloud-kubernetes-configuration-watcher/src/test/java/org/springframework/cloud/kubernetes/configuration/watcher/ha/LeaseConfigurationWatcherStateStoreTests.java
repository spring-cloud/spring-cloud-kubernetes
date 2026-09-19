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

package org.springframework.cloud.kubernetes.configuration.watcher.ha;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;
import io.kubernetes.client.openapi.models.V1Lease;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.kubernetes.client.openapi.JSON.serialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaseConfigurationWatcherStateStoreTests {

	private static final String LEASE_NAME = "configuration-watcher-ha";

	private static final String LEASE_NAMESPACE = "default";

	private static final String LEASE_URL = "/apis/coordination.k8s.io/v1/namespaces/" + LEASE_NAMESPACE + "/leases/"
			+ LEASE_NAME;

	private static final String LEASE_COLLECTION_URL = "/apis/coordination.k8s.io/v1/namespaces/" + LEASE_NAMESPACE
			+ "/leases";

	private static final String CONFIGMAP_RESOURCE_VERSION_ANNOTATION = "spring.cloud.kubernetes.configuration.watcher/configmap-resource-version";

	private static final String SECRET_RESOURCE_VERSION_ANNOTATION = "spring.cloud.kubernetes.configuration.watcher/secret-resource-version";

	private static WireMockServer wireMockServer;

	private static LeaseConfigurationWatcherStateStore stateStore;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		configureFor("localhost", wireMockServer.port());
		stateStore = new LeaseConfigurationWatcherStateStore(
				new CoordinationV1Api(new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build()),
				new ConfigurationWatcherHaProperties());
	}

	@AfterEach
	void afterEach() {
		wireMockServer.resetAll();
	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
	}

	/**
	 * <pre>
	 * 	- the HA lease already exists
	 * 	- it stores the last seen ConfigMap and Secret resource versions
	 * 	- readOrCreate returns those versions so the leader can resume from them
	 * </pre>
	 */
	@Test
	void readOrCreateReturnsExistingStateFromLeaseAnnotations() {
		String configMapResourceVersion = "11";
		String secretResourceVersion = "22";

		V1Lease lease = leaseWithAnnotations(configMapResourceVersion, secretResourceVersion);
		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(200).withBody(serialize(lease))));

		ConfigurationWatcherState state = stateStore.readOrCreate();

		assertThat(state.configMapResourceVersions()).containsEntry(LEASE_NAMESPACE, configMapResourceVersion);
		assertThat(state.secretResourceVersions()).containsEntry(LEASE_NAMESPACE, secretResourceVersion);
		wireMockServer.verify(getRequestedFor(urlEqualTo(LEASE_URL)));
	}

	/**
	 * <pre>
	 * 	- the leader starts and no HA lease exists yet
	 * 	- readOrCreate creates the lease in the configured namespace
	 * 	- the returned state is empty because there is no previous checkpoint
	 * </pre>
	 */
	@Test
	void readOrCreateCreatesLeaseAndReturnsEmptyStateWhenLeaseIsMissing() {
		V1Lease lease = new V1Lease().metadata(new V1ObjectMeta().name(LEASE_NAME).namespace(LEASE_NAMESPACE));

		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(404)));
		stubFor(post(urlEqualTo(LEASE_COLLECTION_URL))
			.willReturn(aResponse().withStatus(200).withBody(serialize(lease))));

		ConfigurationWatcherState state = stateStore.readOrCreate();

		assertThat(state).isEqualTo(ConfigurationWatcherState.EMPTY);
		wireMockServer.verify(getRequestedFor(urlEqualTo(LEASE_URL)));
		wireMockServer.verify(postRequestedFor(urlEqualTo(LEASE_COLLECTION_URL))
			.withRequestBody(matchingJsonPath("$.metadata.name", equalTo(LEASE_NAME)))
			.withRequestBody(matchingJsonPath("$.metadata.namespace", equalTo(LEASE_NAMESPACE))));
	}

	/**
	 * <pre>
	 * 	- the HA lease already contains a stored Secret resource version
	 * 	- write stores the new ConfigMap resource version on the same lease
	 * 	- existing annotations that are not overwritten stay in place
	 * </pre>
	 */
	@Test
	void writeUpdatesLeaseAnnotations() {
		String existingSecretResourceVersion = "22";
		String configMapResourceVersion = "11";

		V1Lease existingLease = leaseWithAnnotations(null, existingSecretResourceVersion);
		V1Lease updatedLease = leaseWithAnnotations(configMapResourceVersion, existingSecretResourceVersion);

		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(200).withBody(serialize(existingLease))));
		stubFor(put(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(200).withBody(serialize(updatedLease))));

		stateStore.writeConfigMapResourceVersion(LEASE_NAMESPACE, configMapResourceVersion);

		wireMockServer.verify(getRequestedFor(urlEqualTo(LEASE_URL)));
		wireMockServer.verify(putRequestedFor(urlEqualTo(LEASE_URL))
			.withRequestBody(
					matchingJsonPath("$.metadata.annotations." + jsonPath(CONFIGMAP_RESOURCE_VERSION_ANNOTATION),
							equalTo(LEASE_NAMESPACE + "=" + configMapResourceVersion)))
			.withRequestBody(matchingJsonPath("$.metadata.annotations." + jsonPath(SECRET_RESOURCE_VERSION_ANNOTATION),
					equalTo(LEASE_NAMESPACE + "=" + existingSecretResourceVersion))));
	}

	/**
	 * <pre>
	 * 	- reading the HA lease fails with an error other than 404
	 * 	- readOrCreate must fail instead of pretending that no previous state exists
	 * </pre>
	 */
	@Test
	void readOrCreateThrowsWhenLeaseReadFailsWithNon404() {
		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(500).withBody("boom")));

		assertThatThrownBy(() -> stateStore.readOrCreate()).isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to read watcher HA lease '" + LEASE_NAME + "' in namespace '" + LEASE_NAMESPACE + "'");
	}

	/**
	 * <pre>
	 * 	- the leader starts and the HA lease does not exist yet
	 * 	- creating that lease fails
	 * 	- readOrCreate must fail instead of returning an empty checkpoint
	 * </pre>
	 */
	@Test
	void readOrCreateThrowsWhenMissingLeaseCanNotBeCreated() {
		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(404)));
		stubFor(post(urlEqualTo(LEASE_COLLECTION_URL)).willReturn(aResponse().withStatus(500).withBody("boom")));

		assertThatThrownBy(() -> stateStore.readOrCreate()).isInstanceOf(IllegalStateException.class)
			.hasMessage(
					"Failed to create watcher HA lease '" + LEASE_NAME + "' in namespace '" + LEASE_NAMESPACE + "'");
	}

	/**
	 * <pre>
	 * 	- the existing HA lease is read successfully
	 * 	- replacing it with the updated annotations fails
	 * 	- write must surface that failure to the caller
	 * </pre>
	 */
	@Test
	void writeThrowsWhenLeaseUpdateFails() {
		String existingSecretResourceVersion = "22";
		String configMapResourceVersion = "11";

		V1Lease existingLease = leaseWithAnnotations(null, existingSecretResourceVersion);

		stubFor(get(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(200).withBody(serialize(existingLease))));
		stubFor(put(urlEqualTo(LEASE_URL)).willReturn(aResponse().withStatus(500).withBody("boom")));

		assertThatThrownBy(() -> stateStore.writeConfigMapResourceVersion(LEASE_NAMESPACE, configMapResourceVersion))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to write watcher HA lease '" + LEASE_NAME + "' in namespace '" + LEASE_NAMESPACE + "'");
	}

	private static V1Lease leaseWithAnnotations(String configMapResourceVersion, String secretResourceVersion) {
		Map<String, String> annotations = new HashMap<>();
		if (configMapResourceVersion != null) {
			annotations.put(CONFIGMAP_RESOURCE_VERSION_ANNOTATION, LEASE_NAMESPACE + "=" + configMapResourceVersion);
		}
		if (secretResourceVersion != null) {
			annotations.put(SECRET_RESOURCE_VERSION_ANNOTATION, LEASE_NAMESPACE + "=" + secretResourceVersion);
		}
		return new V1Lease()
			.metadata(new V1ObjectMeta().name(LEASE_NAME).namespace(LEASE_NAMESPACE).annotations(annotations));
	}

	private static String jsonPath(String annotationName) {
		return "['" + annotationName + "']";
	}

}
