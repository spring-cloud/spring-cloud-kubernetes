/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.config.reload;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.api.model.WatchEventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
public class EventBasedConfigurationChangeDetectorTests {

	@ClassRule
	public static KubernetesServer mockServer = new KubernetesServer(false);

	private static KubernetesClient mockClient;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = mockServer.getClient().inNamespace("test");
	}

	@Test
	public void reWatchConfigMapsOnFailure() throws InterruptedException {
		ConfigMap sample = new ConfigMapBuilder().withNewMetadata().withName("sample")
				.endMetadata().addToData("key", "value").build();
		ConfigMap modifiedSample = new ConfigMapBuilder().withNewMetadata()
				.withName("sample").endMetadata().addToData("key", "newValue").build();
		mockServer.expect().get().withPath("/api/v1/namespaces/test/configmaps/sample")
				.andReturn(HttpURLConnection.HTTP_OK, sample).once();
		mockServer.expect().get().withPath("/api/v1/namespaces/test/configmaps/sample")
				.andReturn(HttpURLConnection.HTTP_OK, modifiedSample).always();

		WatchEvent outdatedEvent = new WatchEventBuilder().withStatusObject(
				new StatusBuilder().withCode(HttpURLConnection.HTTP_GONE)
						.withMessage("too old resource version: 35168187 (41169815)")
						.build())
				.build();
		WatchEvent addedEvent = new WatchEvent(modifiedSample, "MODIFIED");
		mockServer.expect().get()
				.withPath("/api/v1/namespaces/test/configmaps?watch=true")
				.andUpgradeToWebSocket().open().waitFor(10L).andEmit(outdatedEvent).done()
				.once();
		mockServer.expect().get()
				.withPath("/api/v1/namespaces/test/configmaps?watch=true")
				.andUpgradeToWebSocket().open().waitFor(10L).andEmit(addedEvent).done()
				.always();

		final CountDownLatch reloadLatch = new CountDownLatch(1);
		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("test",
				reloadLatch::countDown);
		ConfigMapConfigProperties configProps = new ConfigMapConfigProperties();
		configProps.setName("sample");
		configProps.setNamespace("test");
		ConfigMapPropertySourceLocator locator = new ConfigMapPropertySourceLocator(
				mockClient, configProps);
		MockEnvironment env = new MockEnvironment();
		env.getPropertySources().addFirst(locator.locate(env));
		ConfigReloadProperties reloadProps = new ConfigReloadProperties();
		EventBasedConfigurationChangeDetector detector = new EventBasedConfigurationChangeDetector(
				env, reloadProps, mockClient, strategy, locator, null);

		detector.watch();
		assertThat(reloadLatch.await(10L, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	public void reWatchSecretsOnFailure() throws InterruptedException {
		Secret sample = new SecretBuilder().withNewMetadata().withName("sample")
				.endMetadata().addToData("key", "value").build();
		Secret modifiedSample = new SecretBuilder().withNewMetadata().withName("sample")
				.endMetadata().addToData("key", "newValue").build();
		mockServer.expect().get().withPath("/api/v1/namespaces/test/secrets/sample")
				.andReturn(HttpURLConnection.HTTP_OK, sample).once();
		mockServer.expect().get().withPath("/api/v1/namespaces/test/secrets/sample")
				.andReturn(HttpURLConnection.HTTP_OK, modifiedSample).always();

		WatchEvent outdatedEvent = new WatchEventBuilder().withStatusObject(
				new StatusBuilder().withCode(HttpURLConnection.HTTP_GONE)
						.withMessage("too old resource version: 35168187 (41169815)")
						.build())
				.build();
		WatchEvent addedEvent = new WatchEvent(modifiedSample, "MODIFIED");
		mockServer.expect().get().withPath("/api/v1/namespaces/test/secrets?watch=true")
				.andUpgradeToWebSocket().open().waitFor(10L).andEmit(outdatedEvent).done()
				.once();
		mockServer.expect().get().withPath("/api/v1/namespaces/test/secrets?watch=true")
				.andUpgradeToWebSocket().open().waitFor(10L).andEmit(addedEvent).done()
				.always();

		final CountDownLatch reloadLatch = new CountDownLatch(1);
		ConfigurationUpdateStrategy strategy = new ConfigurationUpdateStrategy("test",
				reloadLatch::countDown);
		SecretsConfigProperties secretsProps = new SecretsConfigProperties();
		secretsProps.setEnableApi(true);
		secretsProps.setName("sample");
		secretsProps.setNamespace("test");
		SecretsPropertySourceLocator locator = new SecretsPropertySourceLocator(
				mockClient, secretsProps);
		MockEnvironment env = new MockEnvironment();
		env.getPropertySources().addFirst(locator.locate(env));
		ConfigReloadProperties reloadProps = new ConfigReloadProperties();
		reloadProps.setMonitoringConfigMaps(false);
		reloadProps.setMonitoringSecrets(true);
		EventBasedConfigurationChangeDetector detector = new EventBasedConfigurationChangeDetector(
				env, reloadProps, mockClient, strategy, null, locator);

		detector.watch();
		assertThat(reloadLatch.await(10L, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	public void verifyConfigChangesAccountsForBootstrapPropertySources() {
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		MockEnvironment env = new MockEnvironment();
		KubernetesClient k8sClient = mock(KubernetesClient.class);
		ConfigMap configMap = new ConfigMap();
		Map<String, String> data = new HashMap();
		data.put("foo", "bar");
		configMap.setData(data);
		MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> mixedOperation = mock(
				MixedOperation.class);
		Resource<ConfigMap, DoneableConfigMap> resource = mock(Resource.class);
		when(resource.get()).thenReturn(configMap);
		when(mixedOperation.withName(eq("myconfigmap"))).thenReturn(resource);
		when(k8sClient.configMaps()).thenReturn(mixedOperation);

		ConfigMapPropertySource configMapPropertySource = new ConfigMapPropertySource(
				k8sClient, "myconfigmap");
		env.getPropertySources()
				.addFirst(new BootstrapPropertySource(configMapPropertySource));

		ConfigurationUpdateStrategy configurationUpdateStrategy = mock(
				ConfigurationUpdateStrategy.class);
		ConfigMapPropertySourceLocator configMapLocator = mock(
				ConfigMapPropertySourceLocator.class);
		SecretsPropertySourceLocator secretsLocator = mock(
				SecretsPropertySourceLocator.class);
		EventBasedConfigurationChangeDetector detector = new EventBasedConfigurationChangeDetector(
				env, configReloadProperties, k8sClient, configurationUpdateStrategy,
				configMapLocator, secretsLocator);
		List<ConfigMapPropertySource> sources = detector
				.findPropertySources(ConfigMapPropertySource.class);
		assertThat(sources.size()).isEqualTo(1);
		assertThat(sources.get(0).getProperty("foo")).isEqualTo("bar");
	}

}
