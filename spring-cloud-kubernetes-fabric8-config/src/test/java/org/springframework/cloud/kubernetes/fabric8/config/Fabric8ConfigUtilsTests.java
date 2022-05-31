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
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.SourceDataEntriesProcessor;
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
		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByLabels(client,
				"spring-k8s", Map.of("color", "red"), new MockEnvironment(),
				SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertEquals(Set.of(), result.getKey());
		Assertions.assertTrue(result.getValue().isEmpty());
	}

	// secret "my-secret" is deployed with label {color:pink}; we search for it by same
	// label and find it.
	@Test
	void testSecretDataByLabelsSecretFound() {
		client.secrets().inNamespace("spring-k8s").create(new SecretBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-secret").withLabels(Map.of("color", "pink")).build())
				.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());

		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByLabels(client,
				"spring-k8s", Map.of("color", "pink"), new MockEnvironment(),
				SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertEquals(Set.of("my-secret"), result.getKey());
		Assertions.assertEquals(Map.of("property", "value"), result.getValue());
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

		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByLabels(client,
				"spring-k8s", Map.of("color", "pink"), new MockEnvironment(),
				SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertEquals(Set.of("my-secret"), result.getKey());
		Assertions.assertEquals(Map.of("key1", "value1"), result.getValue());
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

		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByLabels(client,
				"spring-k8s", Map.of("color", "pink"), new MockEnvironment(),
				SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertTrue(result.getKey().contains("my-secret"));
		Assertions.assertTrue(result.getKey().contains("my-secret-2"));

		Assertions.assertEquals(2, result.getValue().size());
		Assertions.assertEquals("value", result.getValue().get("property"));
		Assertions.assertEquals("value-2", result.getValue().get("property-2"));
	}

	// secret "my-secret" is deployed; we search for it by name and do not find it.
	@Test
	void testSecretDataByNameSecretNotFound() {
		client.secrets().inNamespace("spring-k8s").create(
				new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build()).build());
		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s",
				Set.of("nope"), new MockEnvironment(), SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertEquals(0, result.getKey().size());
	}

	// secret "my-secret" is deployed; we search for it by name and find it.
	@Test
	void testSecretDataByNameSecretFound() {
		client.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withMetadata(new ObjectMetaBuilder().withName("my-secret").build())
						.addToData(Map.of("property", Base64.getEncoder().encodeToString("value".getBytes()))).build());

		Map.Entry<Set<String>, Map<String, Object>> result = Fabric8ConfigUtils.secretsDataByName(client, "spring-k8s",
				Set.of("my-secret"), new MockEnvironment(), SourceDataEntriesProcessor::processAllEntries);
		Assertions.assertEquals(1, result.getKey().size());
		Assertions.assertEquals("value", result.getValue().get("property"));
	}

}
