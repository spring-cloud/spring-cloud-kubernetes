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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Test;

import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySourceLocator;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
public class EventBasedConfigurationChangeDetectorTests {

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

		ConfigMapPropertySource configMapPropertySource = new ConfigMapPropertySource(k8sClient, "myconfigmap");
		env.getPropertySources().addFirst(new BootstrapPropertySource(configMapPropertySource));

		ConfigurationUpdateStrategy configurationUpdateStrategy = mock(ConfigurationUpdateStrategy.class);
		ConfigMapPropertySourceLocator configMapLocator = mock(ConfigMapPropertySourceLocator.class);
		EventBasedConfigMapChangeDetector detector = new EventBasedConfigMapChangeDetector(env, configReloadProperties,
				k8sClient, configurationUpdateStrategy, configMapLocator);
		List<ConfigMapPropertySource> sources = detector.findPropertySources(ConfigMapPropertySource.class);
		assertThat(sources.size()).isEqualTo(1);
		assertThat(sources.get(0).getProperty("foo")).isEqualTo("bar");
	}

}
