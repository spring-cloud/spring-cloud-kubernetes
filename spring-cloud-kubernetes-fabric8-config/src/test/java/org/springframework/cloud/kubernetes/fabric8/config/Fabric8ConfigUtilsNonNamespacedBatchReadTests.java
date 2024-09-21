/*
 * Copyright 2013-2024 the original author or authors.
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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.mock.env.MockEnvironment;

import static org.springframework.cloud.kubernetes.commons.config.Constants.APPLICATION_YAML;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ConfigUtilsNonNamespacedBatchReadTests {

	private static final boolean NAMESPACED_BATCH_READ = false;

	private KubernetesClient client;

	@AfterEach
	void afterEach() {
		new Fabric8ConfigMapsNamespaceBatched().discardAll();
		new Fabric8SecretsNamespaceBatched().discardAll();
	}

	/**
	 * <pre>
	 *  	- secret 'my-secret' is deployed without any labels
	 *  	- we search for it by labels 'color=red' and do not find it.
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsSecretNotFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build()).build())
			.create();
		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "red"), new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Map.of(), result.data());
		Assertions.assertTrue(result.names().isEmpty());
	}

	/**
	 * <pre>
	 *		- secret 'my-secret' is deployed with label '{color:pink}'
	 *		- we search for it by same label and find it.
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsSecretFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes())))
				.build())
			.create();

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of("my-secret"), result.names());
		Assertions.assertEquals(Map.of("property", "value"), result.data());
	}

	/**
	 * <pre>
	 * 		- secret 'my-secret' is deployed with label '{color:pink}'
	 * 		- we search for it by same label and find it.
	 * 		- This secret contains a single .yaml property, as such, it gets some special treatment.
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsSecretFoundWithPropertyFile() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of(APPLICATION_YAML, Base64.getEncoder().encodeToString("key1: value1".getBytes())))
				.build())
			.create();

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of("my-secret"), result.names());
		Assertions.assertEquals(Map.of("key1", "value1"), result.data());
	}

	/**
	 * <pre>
	 * 		- secrets 'my-secret' and 'my-secret-2' are deployed with label {color:pink}
	 * 		- we search for them by same label and find them.
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsTwoSecretsFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes())))
				.build())
			.create();

		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(
						new ObjectMetaBuilder().withName("my-secret-2").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property-2", Base64.getEncoder().encodeToString("value-2".getBytes())))
				.build())
			.create();

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("color", "pink"), new MockEnvironment(), NAMESPACED_BATCH_READ);
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
	 *     - we search by labels "color=blue, tag=fits", as such find two secrets: "blue-circle-secret"
	 *       and "blue-square-secret".
	 * </pre>
	 */
	@Test
	void testSecretDataByLabelsThreeSecretsFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("blue-circle-secret")
					.withLabels(Map.of("color", "blue", "shape", "circle", "tag", "fit"))
					.build())
				.addToData(Map.of("one", Base64.getEncoder().encodeToString("1".getBytes())))
				.build())
			.create();

		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("blue-square-secret")
					.withLabels(Map.of("color", "blue", "shape", "square", "tag", "fit"))
					.build())
				.addToData(Map.of("two", Base64.getEncoder().encodeToString("2".getBytes())))
				.build())
			.create();

		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("blue-triangle-secret")
					.withLabels(Map.of("color", "blue", "shape", "triangle", "tag", "no-fit"))
					.build())
				.addToData(Map.of("three", Base64.getEncoder().encodeToString("3".getBytes())))
				.build())
			.create();

		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("blue-square-secret-k8s")
					.withLabels(Map.of("color", "blue", "shape", "triangle", "tag", "no-fit"))
					.build())
				.addToData(Map.of("four", Base64.getEncoder().encodeToString("4".getBytes())))
				.build())
			.create();

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByLabels(client, "spring-k8s",
				Map.of("tag", "fit", "color", "blue"), new MockEnvironment(), NAMESPACED_BATCH_READ);

		Assertions.assertTrue(result.names().contains("blue-circle-secret"));
		Assertions.assertTrue(result.names().contains("blue-square-secret"));

		Assertions.assertEquals(2, result.data().size());
		Assertions.assertEquals("1", result.data().get("one"));
		Assertions.assertEquals("2", result.data().get("two"));
	}

	/**
	 * <pre>
	 * 		- secret 'my-secret' is deployed; we search for it by name and do not find it.
	 * </pre>
	 */
	@Test
	void testSecretDataByNameSecretNotFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build()).build())
			.create();
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nope");
		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(0, result.names().size());
		Assertions.assertEquals(0, result.data().size());
	}

	/**
	 * <pre>
	 * 		- secret "my-secret" is deployed; we search for it by name and find it.
	 * </pre>
	 */
	@Test
	void testSecretDataByNameSecretFound() {
		client.secrets()
			.inNamespace("spring-k8s")
			.resource(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes())))
				.build())
			.create();
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-secret");

		MultipleSourcesContainer result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(1, result.names().size());
		Assertions.assertEquals("value", result.data().get("property"));
	}

	/**
	 * <pre>
	 * 		- config-map "my-config-map" is deployed without any data
	 * 		- we search for it by name and find it; but it has no data.
	 * </pre>
	 */
	@Test
	void testConfigMapsDataByNameFoundNoData() {
		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
				.build())
			.create();
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-config-map");

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of("my-config-map"), result.names());
		Assertions.assertTrue(result.data().isEmpty());
	}

	/**
	 * <pre>
	 *     	- config-map "my-config-map" is deployed; we search for it and do not find it.
	 * </pre>
	 */
	@Test
	void testConfigMapsDataByNameNotFound() {
		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
				.build())
			.create();
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-config-map-not-found");
		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of(), result.names());
		Assertions.assertTrue(result.data().isEmpty());
	}

	/**
	 * <pre>
	 *     - config-map "my-config-map" is deployed; we search for it and find it
	 * </pre>
	 */
	@Test
	void testConfigMapDataByNameFound() {
		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
				.addToData(Map.of("property", "value"))
				.build())
			.create();

		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-config-map");

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of("my-config-map"), result.names());
		Assertions.assertEquals(Map.of("property", "value"), result.data());
	}

	/**
	 * <pre>
	 *     - config-map "my-config-map" is deployed
	 *     - we search for it and find it
	 *     - it contains a single .yaml property, as such it gets some special treatment
	 * </pre>
	 */
	@Test
	void testConfigMapDataByNameFoundWithPropertyFile() {
		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
				.addToData(Map.of(APPLICATION_YAML, "key1: value1"))
				.build())
			.create();

		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-config-map");

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertEquals(Set.of("my-config-map"), result.names());
		Assertions.assertEquals(Map.of("key1", "value1"), result.data());
	}

	/**
	 * <pre>
	 *     - config-map "my-config-map" and "my-config-map-2" are deployed
	 *     - we search and find them.
	 * </pre>
	 */
	@Test
	void testConfigMapDataByNameTwoFound() {
		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map").build())
				.addToData(Map.of("property", "value"))
				.build())
			.create();

		client.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withMetadata(new ObjectMetaBuilder().withName("my-config-map-2").build())
				.addToData(Map.of("property-2", "value-2"))
				.build())
			.create();

		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("my-config-map");
		names.add("my-config-map-2");

		MultipleSourcesContainer result = Fabric8ConfigUtils.configMapsDataByName(client, "spring-k8s", names,
				new MockEnvironment(), NAMESPACED_BATCH_READ);
		Assertions.assertTrue(result.names().contains("my-config-map"));
		Assertions.assertTrue(result.names().contains("my-config-map-2"));

		Assertions.assertEquals(2, result.data().size());
		Assertions.assertEquals("value", result.data().get("property"));
		Assertions.assertEquals("value-2", result.data().get("property-2"));
	}

}
