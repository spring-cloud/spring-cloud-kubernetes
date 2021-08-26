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

package org.springframework.cloud.kubernetes.client.config;

import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.util.StringUtils;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientConfigUtils {

	private KubernetesClientConfigUtils() {
	}

	// only use KubernetesClientProperties/KubernetesNamespaceProvider if the namespace in the
	// normalized source is not present
	static final BiFunction<KubernetesClientProperties, KubernetesNamespaceProvider, Function<String, String>>
		NAMESPACE_PROVIDER = (properties, provider) -> normalizedNamespace -> {
		if (StringUtils.hasText(normalizedNamespace)) {
			return normalizedNamespace;
		}
		return provider != null ? provider.getNamespace() : properties.getNamespace();
	};

	// deprecated methods below are not used and must be removed at some point in time
	@Deprecated
	public static String getNamespace(ConfigMapConfigProperties.NormalizedSource normalizedSource,
			String fallbackNamespace) {
		String normalizedNamespace = normalizedSource.getNamespace();
		return StringUtils.hasText(normalizedNamespace) ? normalizedNamespace : fallbackNamespace;
	}

	@Deprecated
	public static String getNamespace(ConfigMapConfigProperties.NormalizedSource normalizedSource,
			KubernetesClientProperties kubernetesClientProperties) {
		if (!StringUtils.hasText(normalizedSource.getNamespace())) {
			return kubernetesClientProperties.getNamespace();
		}
		else {
			return normalizedSource.getNamespace();
		}
	}

	@Deprecated
	public static String getNamespace(SecretsConfigProperties.NormalizedSource normalizedSource,
			KubernetesClientProperties kubernetesClientProperties) {
		if (!StringUtils.hasText(normalizedSource.getNamespace())) {
			return kubernetesClientProperties.getNamespace();
		}
		else {
			return normalizedSource.getNamespace();
		}
	}

	@Deprecated
	public static String getNamespace(SecretsConfigProperties.NormalizedSource normalizedSource,
			String fallbackNamespace) {
		String normalizedNamespace = normalizedSource.getNamespace();
		return StringUtils.hasText(normalizedNamespace) ? normalizedNamespace : fallbackNamespace;
	}

}
