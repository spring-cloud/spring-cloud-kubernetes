package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;

import java.util.List;
import java.util.function.Predicate;

/**
 * Cacheable Fabric8 Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
class Fabric8CacheableDiscoveryClient extends Fabric8AbstractBlockingDiscoveryClient {

	Fabric8CacheableDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
		ServicePortSecureResolver servicePortSecureResolver, KubernetesNamespaceProvider namespaceProvider,
		Predicate<Service> predicate) {

		super(client, kubernetesDiscoveryProperties, servicePortSecureResolver, namespaceProvider, predicate);
	}

	@Override
	@Cacheable("fabric8-discovery-services")
	public List<String> getServices() {
		return super.getServices();
	}

	@Override
	@Cacheable("fabric8-discovery-instances")
	public List<ServiceInstance> getInstances(String serviceId) {
		return super.getInstances(serviceId);
	}

	@Override
	public String description() {
		return "Fabric8 Kubernetes Discovery Client";
	}

	@Override
	public int getOrder() {
		return super.getOrder();
	}
}
