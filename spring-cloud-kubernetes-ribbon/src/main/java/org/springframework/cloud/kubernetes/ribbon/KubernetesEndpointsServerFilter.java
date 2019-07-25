package org.springframework.cloud.kubernetes.ribbon;

import io.fabric8.kubernetes.api.model.EndpointAddress;

public interface KubernetesEndpointsServerFilter {

	public boolean isFilter(String serviceId,EndpointAddress adress);	
	
}
