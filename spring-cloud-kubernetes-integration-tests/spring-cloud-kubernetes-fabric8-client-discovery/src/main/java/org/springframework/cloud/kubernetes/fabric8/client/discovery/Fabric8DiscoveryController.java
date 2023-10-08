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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.util.List;

import io.fabric8.kubernetes.api.model.Endpoints;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
public class Fabric8DiscoveryController {

	private final KubernetesDiscoveryClient discoveryClient;

	public Fabric8DiscoveryController(ObjectProvider<KubernetesDiscoveryClient> discoveryClient) {
		KubernetesDiscoveryClient[] local = new KubernetesDiscoveryClient[1];
		discoveryClient.ifAvailable(x -> local[0] = x);
		this.discoveryClient = local[0];
	}

	@GetMapping("/services")
	public List<String> allServices() {
		return discoveryClient.getServices();
	}

	@GetMapping("/endpoints/{serviceId}")
	public List<Endpoints> getEndPointsList(@PathVariable("serviceId") String serviceId) {
		return discoveryClient.getEndPointsList(serviceId);
	}

	@GetMapping("/service-instances/{serviceId}")
	public List<ServiceInstance> serviceInstances(@PathVariable("serviceId") String serviceId) {
		return discoveryClient.getInstances(serviceId);
	}

}
