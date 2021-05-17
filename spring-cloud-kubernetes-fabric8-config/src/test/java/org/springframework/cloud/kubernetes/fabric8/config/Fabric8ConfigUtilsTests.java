package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
public class Fabric8ConfigUtilsTests {

	@ClassRule
	public static final KubernetesServer server = new KubernetesServer();

	@Test
	public void testGetApplicationNamespaceNotPresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(server.getClient(), "", "target");
		assertThat(result).isEqualTo("test");
	}

	@Test
	public void testGetApplicationNamespacePresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(server.getClient(), "namespace", "target");
		assertThat(result).isEqualTo("namespace");
	}

}
