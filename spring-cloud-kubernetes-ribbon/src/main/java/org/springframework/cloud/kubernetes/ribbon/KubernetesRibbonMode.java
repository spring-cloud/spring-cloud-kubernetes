
package org.springframework.cloud.kubernetes.ribbon;

/**
 * the KubernetesRibbonMode description.
 *
 * @author wuzishu
 */
public enum KubernetesRibbonMode {

	/**
	 * using pod ip and port
	 */
	POD,
	/**
	 * using kubernetes service name and port
	 */
	SERVICE

}
