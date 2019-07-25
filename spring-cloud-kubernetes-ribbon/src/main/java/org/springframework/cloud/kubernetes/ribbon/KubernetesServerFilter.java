package org.springframework.cloud.kubernetes.ribbon;

import io.fabric8.kubernetes.api.model.EndpointAddress;

/**
 * Kubernetes {@link ServerList}.
 *
 * @author fuheping
 */
public interface KubernetesServerFilter {
	public boolean isExclusion(EndpointAddress address);

}
