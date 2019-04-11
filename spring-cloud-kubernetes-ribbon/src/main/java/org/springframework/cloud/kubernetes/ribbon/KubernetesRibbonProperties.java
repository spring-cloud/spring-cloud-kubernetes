

package org.springframework.cloud.kubernetes.ribbon;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * the KubernetesRibbonProperties description.
 *
 * @author wuzishu
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.ribbon")
public class KubernetesRibbonProperties {

	/**
	 * @see {@link KubernetesRibbonMode} setting ribbon server list with ip of pod or
	 * service name. default value is POD.
	 */
	private KubernetesRibbonMode mode;

	/**
	 * Gets mode.
	 * @return the mode
	 */
	public KubernetesRibbonMode getMode() {
		return mode;
	}

	/**
	 * Sets mode.
	 * @param mode the mode
	 */
	public void setMode(KubernetesRibbonMode mode) {
		this.mode = mode;
	}

}
