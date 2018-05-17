package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.Constants.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.core.env.PropertyResolver;

final class OnKubernetesConditionSupport {

	private OnKubernetesConditionSupport() {

	}

	// We need to create a KubernetesClient ourselves
	// since the KubernetesClient Bean is not available when the Condition is evaluated
	static KubernetesClient getKubernetesClient(PropertyResolver propertyResolver) {
		Config base = Config.autoConfigure(null);
		Config properties = new ConfigBuilder(base)
			.withMasterUrl(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".master-url",
				base.getMasterUrl()
				)
			)
			.withCaCertFile(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".ca-cert-file",
				base.getCaCertFile()
				)
			)
			.withCaCertData(propertyResolver.getProperty(
				SPRING_CLOUD_KUBERNETES_CLIENT_PROPS + ".ca-cert-data",
				base.getCaCertData()
				)
			)
			.build();

		return new DefaultKubernetesClient(properties);
	}

	static boolean isServerUp(KubernetesClient kubernetesClient) {
		try {
			kubernetesClient.rootPaths();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
