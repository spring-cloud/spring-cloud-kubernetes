/*
 * Copyright 2013-present the original author or authors.
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

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Only needed to get a hold of {@link Fabric8ConfigMapPropertySource}.
 *
 * @author wind57
 */
public final class Fabric8ConfigMapPropertySourceProvider {

	private Fabric8ConfigMapPropertySourceProvider() {

	}

	public static Fabric8ConfigMapPropertySource configMapPropertySource(KubernetesClient kubernetesClient) {
		NamedConfigMapNormalizedSource namedConfigMapNormalizedSource = new NamedConfigMapNormalizedSource("configmap",
				"default", true, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(kubernetesClient, namedConfigMapNormalizedSource,
				"default", new MockEnvironment());
		return new Fabric8ConfigMapPropertySource(context);
	}

	public static Fabric8SecretsPropertySource secretPropertySource(KubernetesClient kubernetesClient) {

		NamedSecretNormalizedSource namedSecretNormalizedSource = new NamedSecretNormalizedSource("secret",
				"default", true, true);

		Fabric8ConfigContext context = new Fabric8ConfigContext(kubernetesClient, namedSecretNormalizedSource,
				"default", new MockEnvironment());
		return new Fabric8SecretsPropertySource(context);
	}

}
