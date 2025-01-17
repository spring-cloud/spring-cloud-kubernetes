package org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.it;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.Application;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Set;

import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.it.TestAssertions.assertLogStatement;
import static org.springframework.cloud.kubernetes.k8s.client.catalog.watcher.it.TestAssertions.invokeAndAssert;

@SpringBootTest(classes = { KubernetesClientCatalogWatchEndpointsNamespaceFilterIT.TestConfig.class, Application.class },
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesClientCatalogWatchEndpointsNamespaceFilterIT extends KubernetesClientCatalogWatchBase {

	@LocalServerPort
	private int port;

	@BeforeEach
	void beforeEach() {

		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);

		Images.loadBusybox(K3S);

		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);

	}

	@AfterEach
	void afterEach() {
		// busybox is deleted as part of the assertions, thus not seen here
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods in two namespaces : a, b
	 *     - we use endpoints
	 *     - we enable namespace filtering for 'default' and 'a'
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service in 'a' and 'b'
	 *     - assert that we receive an empty response
	 * </pre>
	 */
	@Test
	void testCatalogWatchWithEndpoints(CapturedOutput output) {
		assertLogStatement(output, "stateGenerator is of type: KubernetesEndpointsCatalogWatch");
		invokeAndAssert(util, Set.of(NAMESPACE_A, NAMESPACE_B), port, NAMESPACE_A);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		ApiClient client() {
			return apiClient();
		}

		@Bean
		@Primary
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties() {
			return discoveryProperties(false);
		}

	}

}
