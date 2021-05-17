/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.spring.extended.controller.KubernetesInformerConfigurer;
import io.kubernetes.client.spring.extended.controller.KubernetesInformerFactoryProcessor;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author Ryan Baxter
 */
class SpringCloudKubernetesInformerConfigurer extends KubernetesInformerConfigurer {

	private final ApiClient apiClient;

	private final SharedInformerFactory sharedInformerFactory;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	private final KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	public SpringCloudKubernetesInformerConfigurer(KubernetesNamespaceProvider kubernetesNamespaceProvider,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties, ApiClient apiClient,
			SharedInformerFactory sharedInformerFactory) {
		super(apiClient, sharedInformerFactory);
		this.apiClient = apiClient;
		this.sharedInformerFactory = sharedInformerFactory;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
		this.kubernetesDiscoveryProperties = kubernetesDiscoveryProperties;
	}

	@Override
	public KubernetesInformerFactoryProcessor getObject() throws Exception {
		return new SpringCloudKubernetesInformerFactoryProcessor(kubernetesDiscoveryProperties,
				kubernetesNamespaceProvider, apiClient, sharedInformerFactory);
	}

	@Override
	public Class<?> getObjectType() {
		return KubernetesInformerFactoryProcessor.class;
	}

}
