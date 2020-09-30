/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.profile;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.springframework.cloud.kubernetes.StandardPodUtils;
import org.springframework.cloud.kubernetes.commons.profile.AbstractKubernetesProfileEnvironmentPostProcessor;

public class KubernetesProfileEnvironmentPostProcessor extends AbstractKubernetesProfileEnvironmentPostProcessor {

	@Override
	protected boolean isInsideKubernetes() {
		try (DefaultKubernetesClient client = new DefaultKubernetesClient()) {
			final StandardPodUtils podUtils = new StandardPodUtils(client);
			return podUtils.isInsideKubernetes();
		}
	}

}
