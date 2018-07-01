package org.springframework.cloud.kubernetes;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.health")
public class KubernetesHealthIndicatorProperties {
	private boolean failOutsidePod = false;


	public boolean isFailOutsidePod() {
		return failOutsidePod;
	}

	public void setFailOutsidePod(boolean failOutsidePod) {
		this.failOutsidePod = failOutsidePod;
	}
}
