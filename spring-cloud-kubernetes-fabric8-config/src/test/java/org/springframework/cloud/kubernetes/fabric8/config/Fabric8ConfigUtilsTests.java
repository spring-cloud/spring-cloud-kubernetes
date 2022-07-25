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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.StrictProfile;
import org.springframework.cloud.kubernetes.commons.config.StrictSource;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ConfigUtilsTests {

	private KubernetesClient client;

	private final DefaultKubernetesClient mockClient = Mockito.mock(DefaultKubernetesClient.class);

	private final KubernetesNamespaceProvider provider = Mockito.mock(KubernetesNamespaceProvider.class);

	private static final LinkedHashSet<StrictProfile> EMPTY = new LinkedHashSet<>();

	@Test
	void testGetApplicationNamespaceNotPresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "", "target", null);
		assertThat(result).isEqualTo("test");
	}

	@Test
	void testGetApplicationNamespacePresent() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "namespace", "target", null);
		assertThat(result).isEqualTo("namespace");
	}

	@Test
	void testNamespaceFromNormalizedSource() {
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "abc", "target", null);
		assertThat(result).isEqualTo("abc");
	}

	@Test
	void testNamespaceFromProvider() {
		Mockito.when(provider.getNamespace()).thenReturn("def");
		String result = Fabric8ConfigUtils.getApplicationNamespace(client, "", "target", provider);
		assertThat(result).isEqualTo("def");
	}

	@Test
	void testNamespaceFromClient() {
		Mockito.when(mockClient.getNamespace()).thenReturn("qwe");
		String result = Fabric8ConfigUtils.getApplicationNamespace(mockClient, "", "target", null);
		assertThat(result).isEqualTo("qwe");
	}

	@Test
	void testNamespaceResolutionFailed() {
		assertThatThrownBy(() -> Fabric8ConfigUtils.getApplicationNamespace(mockClient, "", "target", null))
				.isInstanceOf(NamespaceResolutionFailedException.class);
	}

	// secret "my-secret" is deployed without any labels; we search for it by labels
	// "color=red" and do not find it.
	@Test
	void testSecretDataByLabelsSecretNotFound() {
		client.secrets().inNamespace("spring-k8s").create(
				new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build()).build());
		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "red"), new MockEnvironment(), EMPTY, false);
		Assertions.assertEquals(Map.of(), result.data());
		Assertions.assertTrue(result.names().isEmpty());
	}

	// secret "my-secret" is deployed with label {color:pink}; we search for it by same
	// label and find it.
	@Test
	void testSecretDataByLabelsSecretFound() {
		client.secrets().inNamespace("spring-k8s").create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), EMPTY, true);

		LinkedHashSet<String> name = new LinkedHashSet<>();
		name.add("my-secret");

		Assertions.assertEquals(name, result.names());
		Assertions.assertEquals(Map.of("property", "value"), result.data());
	}

	// secret "my-secret" is deployed with label {color:pink}; we search for it by same
	// label and find it. This secret contains a single .yaml property, as such
	// it gets some special treatment.
	@Test
	void testSecretDataByLabelsSecretFoundWithPropertyFile() {
		client.secrets().inNamespace("spring-k8s").create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("application.yaml", Base64.getEncoder().encodeToString("key1: value1".getBytes())))
				.build());

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), EMPTY, true);

		LinkedHashSet<String> name = new LinkedHashSet<>();
		name.add("my-secret");
		Assertions.assertEquals(name, result.names());
		Assertions.assertEquals(Map.of("key1", "value1"), result.data());
	}

	// secrets "my-secret" and "my-secret-2" are deployed with label {color:pink};
	// we search for them by same label and find them.
	@Test
	void testSecretDataByLabelsTwoSecretsFound() {
		client.secrets().inNamespace("spring-k8s").create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s").create(new SecretBuilder()
				.withMetadata(
						new ObjectMetaBuilder().withName("my-secret-2").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property-2", Base64.getEncoder().encodeToString("value-2".getBytes()))).build());

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), EMPTY, true);
		Assertions.assertTrue(result.names().contains("my-secret"));
		Assertions.assertTrue(result.names().contains("my-secret-2"));

		Assertions.assertEquals(2, result.data().size());
		Assertions.assertEquals("value", result.data().get("property"));
		Assertions.assertEquals("value-2", result.data().get("property-2"));
	}

	/**
	 * <pre>
	 *     - secret deployed with name "blue-circle-secret" and labels "color=blue, shape=circle, tag=fit"
	 *     - secret deployed with name "blue-square-secret" and labels "color=blue, shape=square, tag=fit"
	 *     - secret deployed with name "blue-triangle-secret" and labels "color=blue, shape=triangle, tag=no-fit"
	 *     - secret deployed with name "blue-square-secret-k8s" and labels "color=blue, shape=triangle, tag=no-fit"
	 *
	 *     - we search by labels "color=blue, tag=fits", as such first find two secrets: "blue-circle-secret"
	 *       and "blue-square-secret".
	 *     - since "k8s" profile is enabled, we also take "blue-square-secret-k8s". Notice that this one does not match
	 *       the initial labels (it has "tag=no-fit"), but it does not matter, we take it anyway.
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsThreeSecretsFound() {
		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder()
						.withMetadata(new ObjectMetaBuilder().withName("blue-circle-secret")
								.withLabels(Map.of("color", "blue", "shape", "circle", "tag", "fit")).build())
						.addToData(Map.of("one", Base64.getEncoder().encodeToString("1".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder()
						.withMetadata(new ObjectMetaBuilder().withName("blue-square-secret")
								.withLabels(Map.of("color", "blue", "shape", "square", "tag", "fit")).build())
						.addToData(Map.of("two", Base64.getEncoder().encodeToString("2".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder()
						.withMetadata(new ObjectMetaBuilder().withName("blue-triangle-secret")
								.withLabels(Map.of("color", "blue", "shape", "triangle", "tag", "no-fit")).build())
						.addToData(Map.of("three", Base64.getEncoder().encodeToString("3".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder()
						.withMetadata(new ObjectMetaBuilder().withName("blue-square-secret-k8s")
								.withLabels(Map.of("color", "blue", "shape", "triangle", "tag", "no-fit")).build())
						.addToData(Map.of("four", Base64.getEncoder().encodeToString("4".getBytes()))).build());

		LinkedHashSet<StrictProfile> name = new LinkedHashSet<>();
		name.add(new StrictProfile("k8s", false));

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("tag", "fit", "color", "blue"), new MockEnvironment(), name, true);

		Assertions.assertTrue(result.names().contains("blue-circle-secret"));
		Assertions.assertTrue(result.names().contains("blue-square-secret"));
		Assertions.assertTrue(result.names().contains("blue-square-secret-k8s"));

		Assertions.assertEquals(3, result.data().size());
		Assertions.assertEquals("1", result.data().get("one"));
		Assertions.assertEquals("2", result.data().get("two"));
		Assertions.assertEquals("4", result.data().get("four"));
	}

	/**
	 * <pre>
	 *     - secret with name "a" and labels {color: red} is present in namespace
	 *     - secret with name "a-dev" and labels {color: red} is present in namespace
	 *     - active profile "dev" exists also.
	 *
	 *     this test makes sure that no failures are present. What we mean by this is :
	 *     - since we search for all secrets by labels first, we will find both "a" and "a-dev"
	 *     - since "dev" is an active profile, "a-dev" will not be considered a 'root' secret.
	 *     - if our implementation would have considered both 'root' secrets (i.e.: we would be computing
	 *       profile based siblings from the above), this test would have failed.
	 * </pre>
	 */
	@Test
	void testSecretsCorrectSourcesAreTaken() {
		client.secrets().inNamespace("spring-k8s")
			.create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("a")
					.withLabels(Map.of("color", "red")).build())
				.addToData(Map.of("a", Base64.getEncoder().encodeToString("a".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s")
			.create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("a-dev")
					.withLabels(Map.of("color", "red")).build())
				.addToData(Map.of("a", Base64.getEncoder().encodeToString("a-dev".getBytes()))).build());

		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("dev", true));

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
			Map.of("color", "red"), environment, profiles, true);

		Assertions.assertNotNull(result);

		LinkedHashSet<String> expectedNames = new LinkedHashSet<>();
		expectedNames.add("a");
		expectedNames.add("a-dev");
		Assertions.assertEquals(expectedNames, result.names());

		Assertions.assertEquals(Map.of("a", "a-dev"), result.data());

	}

	// secret "my-secret" is deployed; we search for it by name and do not find it.
	@Test
	void testSecretDataByNameSecretNotFound() {
		client.secrets().inNamespace("spring-k8s").create(
				new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build()).build());
		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("nope", false));
		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s", names,
				new MockEnvironment());
		Assertions.assertEquals(0, result.names().size());
		Assertions.assertEquals(0, result.data().size());
	}

	// secret "my-secret" is deployed; we search for it by name and find it.
	@Test
	void testSecretDataByNameSecretFound() {
		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build())
						.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());
		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-secret", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s", names,
				new MockEnvironment());
		Assertions.assertEquals(1, result.names().size());
		Assertions.assertEquals("value", result.data().get("property"));
	}

	// secrets "my-secret" and "my-secret-2" are deployed;
	// we search for them by name label and find them.
	@Test
	void testSecretDataByNameTwoSecretsFound() {
		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build())
						.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());

		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret-2").build())
						.addToData(Map.of("property-2", Base64.getEncoder().encodeToString("value-2".getBytes())))
						.build());
		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-secret", true));
		names.add(new StrictSource("my-secret-2", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s", names,
				new MockEnvironment());
		Assertions.assertTrue(result.names().contains("my-secret"));
		Assertions.assertTrue(result.names().contains("my-secret-2"));

		Assertions.assertEquals(2, result.data().size());
		Assertions.assertEquals("value", result.data().get("property"));
		Assertions.assertEquals("value-2", result.data().get("property-2"));
	}

	// config-map "my-config-map" is deployed without any data; we search for it by name
	// and find it; but it has no data.
	@Test
	void testConfigMapsDataByNameFoundNoData() {
		client.configMaps().inNamespace("spring-k8s").create(
				new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build()).build());
		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-config-map", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment());

		LinkedHashSet<String> name = new LinkedHashSet<>();
		name.add("my-config-map");

		Assertions.assertEquals(name, result.names());
		Assertions.assertTrue(result.data().isEmpty());
	}

	// config-map "my-config-map" is deployed; we search for it and do not find it.
	@Test
	void testConfigMapsDataByNameNotFound() {
		client.configMaps().inNamespace("spring-k8s").create(
				new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build()).build());
		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-config-map-not-found", false));
		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment());
		Assertions.assertEquals(new LinkedHashSet<>(), result.names());
		Assertions.assertTrue(result.data().isEmpty());
	}

	// config-map "my-config-map" is deployed; we search for it and find it
	@Test
	void testConfigMapDataByNameFound() {
		client.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
						.addToData(Map.of("property", "value")).build());

		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-config-map", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment());

		LinkedHashSet<String> name = new LinkedHashSet<>();
		name.add("my-config-map");
		Assertions.assertEquals(name, result.names());
		Assertions.assertEquals(Map.of("property", "value"), result.data());
	}

	// config-map "my-config-map" is deployed; we search for it and find it.
	// It contains a single .yaml property, as such it gets some special treatment.
	@Test
	void testConfigMapDataByNameFoundWithPropertyFile() {
		client.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
						.addToData(Map.of("application.yaml", "key1: value1")).build());

		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-config-map", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment());

		LinkedHashSet<String> name = new LinkedHashSet<>();
		name.add("my-config-map");

		Assertions.assertEquals(name, result.names());
		Assertions.assertEquals(Map.of("key1", "value1"), result.data());
	}

	// config-map "my-config-map" and "my-config-map-2" are deployed;
	// we search and find them.
	@Test
	void testConfigMapDataByNameTwoFound() {
		client.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
						.addToData(Map.of("property", "value")).build());

		client.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map-2").build())
						.addToData(Map.of("property-2", "value-2")).build());

		LinkedHashSet<StrictSource> names = new LinkedHashSet<>();
		names.add(new StrictSource("my-config-map", true));
		names.add(new StrictSource("my-config-map-2", true));

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment());
		Assertions.assertTrue(result.names().contains("my-config-map"));
		Assertions.assertTrue(result.names().contains("my-config-map-2"));

		Assertions.assertEquals(2, result.data().size());
		Assertions.assertEquals("value", result.data().get("property"));
		Assertions.assertEquals("value-2", result.data().get("property-2"));
	}

	@Test
	void testNamespacesFromProperties() {
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		configReloadProperties.setNamespaces(Set.of("non-default"));
		Set<String> namespaces = Fabric8ConfigUtils.namespaces(null,
				new KubernetesNamespaceProvider(new MockEnvironment()), configReloadProperties, "configmap");
		Assertions.assertEquals(1, namespaces.size());
		Assertions.assertEquals(namespaces.iterator().next(), "non-default");
	}

	@Test
	void testNamespacesFromProvider() {
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.client.namespace", "some");
		KubernetesNamespaceProvider provider = new KubernetesNamespaceProvider(environment);
		Set<String> namespaces = Fabric8ConfigUtils.namespaces(null, provider, configReloadProperties, "configmap");
		Assertions.assertEquals(1, namespaces.size());
		Assertions.assertEquals(namespaces.iterator().next(), "some");
	}

}
