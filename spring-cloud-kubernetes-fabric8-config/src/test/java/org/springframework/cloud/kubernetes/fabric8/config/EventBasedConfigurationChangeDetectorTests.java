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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.fabric8.config.reload.EventBasedConfigMapChangeDetector;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class EventBasedConfigurationChangeDetectorTests {

	@SuppressWarnings("unchecked")
	@Test
	void verifyConfigChangesAccountsForBootstrapPropertySources() {
		ConfigReloadProperties configReloadProperties = new ConfigReloadProperties();
		MockEnvironment env = new MockEnvironment();
		KubernetesClient k8sClient = mock(KubernetesClient.class);
		ConfigMap configMap = new ConfigMap();
		Map<String, String> data = new HashMap<>();
		data.put("foo", "bar");
		configMap.setData(data);
		MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> mixedOperation = mock(MixedOperation.class);
		when(k8sClient.configMaps()).thenReturn(mixedOperation);
		Resource<ConfigMap> resource = mock(Resource.class);

		when(resource.get()).thenReturn(configMap);
		when(mixedOperation.withName(eq("myconfigmap"))).thenReturn(resource);
		when(mixedOperation.inNamespace("default")).thenReturn(mixedOperation);
		when(k8sClient.getNamespace()).thenReturn("default");

		NormalizedSource source = new NamedConfigMapNormalizedSource("myconfigmap", "default", true, false);
		Fabric8ConfigContext context = new Fabric8ConfigContext(k8sClient, source, "default", env);
		Fabric8ConfigMapPropertySource fabric8ConfigMapPropertySource = new Fabric8ConfigMapPropertySource(context);
		env.getPropertySources().addFirst(new BootstrapPropertySource<>(fabric8ConfigMapPropertySource));

		ConfigurationUpdateStrategy configurationUpdateStrategy = mock(ConfigurationUpdateStrategy.class);
		Fabric8ConfigMapPropertySourceLocator configMapLocator = mock(Fabric8ConfigMapPropertySourceLocator.class);
		EventBasedConfigMapChangeDetector detector = new EventBasedConfigMapChangeDetector(env, configReloadProperties,
				k8sClient, configurationUpdateStrategy, configMapLocator);
		List<Fabric8ConfigMapPropertySource> sources = detector
				.findPropertySources(Fabric8ConfigMapPropertySource.class);
		assertThat(sources.size()).isEqualTo(1);
		assertThat(sources.get(0).getProperty("foo")).isEqualTo("bar");
	}

}
