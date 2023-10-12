/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
public class DiscoveryController {

	private final KubernetesInformerDiscoveryClient discoveryClient;

	public DiscoveryController(ObjectProvider<KubernetesInformerDiscoveryClient> discoveryClient) {
		KubernetesInformerDiscoveryClient[] local = new KubernetesInformerDiscoveryClient[1];
		discoveryClient.ifAvailable(x -> local[0] = x);
		this.discoveryClient = local[0];
	}

	@GetMapping("/services")
	public List<String> allServices() {
		return discoveryClient.getServices();
	}

	@GetMapping("/service-instances/{serviceId}")
	public List<ServiceInstance> serviceInstances(@PathVariable("serviceId") String serviceId) {
		return discoveryClient.getInstances(serviceId);
	}

}
