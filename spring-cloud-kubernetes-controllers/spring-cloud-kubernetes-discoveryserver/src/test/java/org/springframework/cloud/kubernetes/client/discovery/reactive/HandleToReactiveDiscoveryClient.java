package org.springframework.cloud.kubernetes.client.discovery.reactive;

import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;

public class HandleToReactiveDiscoveryClient extends KubernetesInformerReactiveDiscoveryClient {

	public HandleToReactiveDiscoveryClient(KubernetesInformerDiscoveryClient kubernetesDiscoveryClient) {
		super(kubernetesDiscoveryClient);
	}
}
