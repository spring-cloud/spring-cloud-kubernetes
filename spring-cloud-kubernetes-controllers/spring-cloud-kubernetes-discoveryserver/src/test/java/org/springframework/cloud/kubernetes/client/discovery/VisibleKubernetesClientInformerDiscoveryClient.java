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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.function.Predicate;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
public class VisibleKubernetesClientInformerDiscoveryClient extends KubernetesClientInformerDiscoveryClient {

	public VisibleKubernetesClientInformerDiscoveryClient(List<SharedInformerFactory> sharedInformerFactories,
			List<Lister<V1Service>> serviceListers, List<Lister<V1Endpoints>> endpointsListers,
			List<SharedInformer<V1Service>> serviceInformers, List<SharedInformer<V1Endpoints>> endpointsInformers,
			KubernetesDiscoveryProperties properties, CoreV1Api coreV1Api, Predicate<V1Service> predicate) {
		super(sharedInformerFactories, serviceListers, endpointsListers, serviceInformers, endpointsInformers,
				properties, coreV1Api, predicate);
	}

}
