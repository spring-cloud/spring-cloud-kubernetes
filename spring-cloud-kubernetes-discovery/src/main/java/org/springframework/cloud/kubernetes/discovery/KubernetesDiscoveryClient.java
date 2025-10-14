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

package org.springframework.cloud.kubernetes.discovery;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ryan Baxter
 */
final class KubernetesDiscoveryClient extends KubernetesAbstractBlockingDiscoveryClient {

	KubernetesDiscoveryClient(RestTemplate rest, KubernetesDiscoveryProperties kubernetesDiscoveryProperties) {
		super(rest, kubernetesDiscoveryProperties);
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

}
