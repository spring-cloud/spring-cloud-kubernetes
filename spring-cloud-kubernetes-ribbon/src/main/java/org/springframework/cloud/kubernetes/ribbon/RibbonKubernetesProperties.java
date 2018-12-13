package org.springframework.cloud.kubernetes.ribbon;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.ribbon")
public class RibbonKubernetesProperties {
	/**
	 * Enables Kubernetes service discovery for ribbon
	 */
	private boolean enabled = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
