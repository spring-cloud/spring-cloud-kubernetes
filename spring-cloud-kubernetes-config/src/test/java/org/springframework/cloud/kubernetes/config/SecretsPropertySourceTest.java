package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Base64;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class) @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class) @TestPropertySource("classpath:/application-secrets.properties") public class SecretsPropertySourceTest {

	private static final String NAMESPACE = "test";
	private static final String SECRET_VALUE = "secretValue";

	@ClassRule public static KubernetesServer server = new KubernetesServer(false, true);

	@Autowired private SecretsPropertySourceLocator propertySourceLocator;
	@Autowired private Environment environment;

	@BeforeClass public static void setUpBeforeClass() {
		KubernetesClient mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
			mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
			"false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, NAMESPACE);

		Secret secret = new SecretBuilder().withNewMetadata()
			.withLabels(singletonMap("foo", "bar")).endMetadata()
			.addToData("secretName", Base64.getEncoder().encodeToString(SECRET_VALUE.getBytes()))
			.build();
		mockClient.secrets().inNamespace(NAMESPACE).create(secret);
	}

	@Test public void toStringShouldNotExposeSecretValues() {
		String actual = propertySourceLocator.locate(environment).toString();

		assertThat(actual).doesNotContain(SECRET_VALUE);
	}
}
