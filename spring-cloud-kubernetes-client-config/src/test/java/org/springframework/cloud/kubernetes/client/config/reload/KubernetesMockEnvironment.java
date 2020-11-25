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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
public class KubernetesMockEnvironment extends AbstractEnvironment {

	private PropertySource propertySource = mock(KubernetesClientSecretsPropertySource.class);

	private Map<String, Object> map = new HashMap<>();

	public KubernetesMockEnvironment(PropertySource mockPropertySource) {
		this.propertySource = mockPropertySource;
		this.getPropertySources().addLast(this.propertySource);
		when(propertySource.getSource()).thenReturn(map);
	}

	public void setProperty(String key, String value) {
		map.put(key, value);
		when(propertySource.getProperty(eq(key))).thenReturn(value);
	}

	public KubernetesMockEnvironment withProperty(String key, String value) {
		this.setProperty(key, value);
		return this;
	}

}
