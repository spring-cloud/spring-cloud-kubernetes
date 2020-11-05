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

package org.springframework.cloud.kubernetes.client.profile;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.kubernetes.client.KubernetesClientPodUtils;
import org.springframework.cloud.kubernetes.commons.profile.AbstractKubernetesProfileEnvironmentPostProcessor;
import org.springframework.core.env.Environment;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientProfileEnvironmentPostProcessor extends AbstractKubernetesProfileEnvironmentPostProcessor {

	@Override
	protected boolean isInsideKubernetes(Environment environment) {
		CoreV1Api api = new CoreV1Api();
		KubernetesClientPodUtils utils = new KubernetesClientPodUtils(api, environment.getProperty(NAMESPACE_PROPERTY));
		return utils.isInsideKubernetes();

	}

}
