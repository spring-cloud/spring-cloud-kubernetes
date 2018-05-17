package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.KubernetesClientProperties.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnKubernetesCondition extends SpringBootCondition {



	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
		AnnotatedTypeMetadata metadata) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");

		if (metadata.isAnnotated(ConditionalOnKubernetes.class.getName())) {
			KubernetesClient kubernetesClient = getKubernetesClient(context.getEnvironment());

			if (!isServerUp(kubernetesClient)) {
				return ConditionOutcome.noMatch(
					message.because("Could not communicate with the Kubernetes server at: "
						+ kubernetesClient.getMasterUrl())
				);
			}

			Map<String, Object> attributes = metadata
				.getAnnotationAttributes(ConditionalOnKubernetes.class.getName());

			Class<?> classClientMustAdaptTo = (Class<?>) attributes.get("classClientMustAdaptTo");
			if (classClientMustAdaptTo == Void.class) {
				return ConditionOutcome.match();
			}

			boolean isClientAdaptableToClass = kubernetesClient.isAdaptable(classClientMustAdaptTo);
			if (!isClientAdaptableToClass) {
				return ConditionOutcome.noMatch(
					message.because("The Kubernetes client was not adaptable to class "
						+ classClientMustAdaptTo)
				);
			}

			return ConditionOutcome.match();
		}

		return ConditionOutcome.noMatch(
			message.because("Unknown Conditional annotation using " +
				this.getClass().getSimpleName())
		);
	}

	// We need to create a KubernetesClient ourselves
	// since the KubernetesClient Bean is not available when the Condition is evaluated
	private KubernetesClient getKubernetesClient(PropertyResolver propertyResolver) {
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

	private boolean isServerUp(KubernetesClient kubernetesClient) {
		try {
			kubernetesClient.rootPaths();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
