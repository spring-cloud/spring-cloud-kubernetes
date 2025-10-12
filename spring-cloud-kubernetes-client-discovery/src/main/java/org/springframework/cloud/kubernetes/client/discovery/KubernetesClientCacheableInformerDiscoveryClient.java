package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.function.Predicate;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
class KubernetesClientCacheableInformerDiscoveryClient extends KubernetesClientAbstractInformerDiscoveryClient {

	KubernetesClientCacheableInformerDiscoveryClient(List<SharedInformerFactory> sharedInformerFactories,
		List<Lister<V1Service>> serviceListers, List<Lister<V1Endpoints>> endpointsListers,
		List<SharedInformer<V1Service>> serviceInformers, List<SharedInformer<V1Endpoints>> endpointsInformers,
		KubernetesDiscoveryProperties properties, CoreV1Api coreV1Api, Predicate<V1Service> predicate) {
		super(sharedInformerFactories, serviceListers, endpointsListers, serviceInformers, endpointsInformers,
			properties, coreV1Api, predicate);
	}

	@Override
	@Cacheable("k8s-native-blocking-discovery-services")
	public List<String> getServices() {
		return super.getServices();
	}

	@Override
	@Cacheable("k8s-native-blocking-discovery-instances")
	public List<ServiceInstance> getInstances(String serviceId) {
		return super.getInstances(serviceId);
	}

	@Override
	public String description() {
		return "Kubernetes Native Discovery Client";
	}

	@Override
	public int getOrder() {
		return super.getOrder();
	}
}
