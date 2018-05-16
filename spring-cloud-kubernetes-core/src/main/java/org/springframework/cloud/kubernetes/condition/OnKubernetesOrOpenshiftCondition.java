package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.Constants.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenshiftAdapterSupport;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnKubernetesOrOpenshiftCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
		AnnotatedTypeMetadata metadata) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		KubernetesClient kubernetesClient = getKubernetesClient(context.getEnvironment());

		if (!isServerUp(kubernetesClient)) {
			return ConditionOutcome.noMatch(
				message.because("Could not communicate with the Kubernetes server at: "
					+ kubernetesClient.getMasterUrl())
			);
		}

		OpenshiftAdapterSupport openshiftAdapterSupport = new OpenshiftAdapterSupport();
		if (metadata.isAnnotated(ConditionalOnKubernetes.class.getName())) {
			Map<String, Object> attributes = metadata
				.getAnnotationAttributes(ConditionalOnKubernetes.class.getName());

			boolean requireVanillaKubernetes = (boolean) attributes.get("requireVanilla");
			boolean serverTypeMatches =
				!requireVanillaKubernetes || !openshiftAdapterSupport.isAdaptable(kubernetesClient);

			if(serverTypeMatches) {
				return ConditionOutcome.match();
			}

			return ConditionOutcome.noMatch(
				message.because("Server is Openshift but vanilla Kubernetes required")
			);
		}
		else if(metadata.isAnnotated(ConditionalOnOpenshift.class.getName())) {
			boolean matchServerType = openshiftAdapterSupport.isAdaptable(kubernetesClient);

			if(matchServerType) {
				return ConditionOutcome.match();
			}

			return ConditionOutcome.noMatch(message.because("Server is not Openshift"));
		}

		return ConditionOutcome.noMatch(
			message.because("Unknown Conditional annotation using " +
				this.getClass().getSimpleName())
		);
	}

	private boolean isServerUp(KubernetesClient kubernetesClient) {
		try {
			kubernetesClient.rootPaths();
			return true;
		} catch (Exception e) {
			return false;
		}
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
}
