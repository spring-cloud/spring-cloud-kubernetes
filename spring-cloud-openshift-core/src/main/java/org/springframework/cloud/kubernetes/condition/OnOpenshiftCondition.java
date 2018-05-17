package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.condition.OnKubernetesConditionSupport.getKubernetesClient;
import static org.springframework.cloud.kubernetes.condition.OnKubernetesConditionSupport.isServerUp;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenshiftAdapterSupport;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnOpenshiftCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
		AnnotatedTypeMetadata metadata) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		if (metadata.isAnnotated(ConditionalOnOpenshift.class.getName())) {
			KubernetesClient kubernetesClient = getKubernetesClient(context.getEnvironment());

			if (!isServerUp(kubernetesClient)) {
				return ConditionOutcome.noMatch(
					message.because("Could not communicate with the Kubernetes server at: "
						+ kubernetesClient.getMasterUrl())
				);
			}

			OpenshiftAdapterSupport openshiftAdapterSupport = new OpenshiftAdapterSupport();

			if(openshiftAdapterSupport.isAdaptable(kubernetesClient)) {
				return ConditionOutcome.match();
			}

			return ConditionOutcome.noMatch(
				message.because("Kubernetes Server is not an Openshift Server")
			);
		}

		return ConditionOutcome.noMatch(
			message.because("Unknown Conditional annotation using " +
				this.getClass().getSimpleName())
		);
	}
}
