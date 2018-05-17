package org.springframework.cloud.kubernetes.condition;

import static org.springframework.cloud.kubernetes.condition.OnKubernetesConditionSupport.getKubernetesClient;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OnKubernetesCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
		AnnotatedTypeMetadata metadata) {

		ConditionMessage.Builder message = ConditionMessage.forCondition("");

		if (metadata.isAnnotated(ConditionalOnKubernetes.class.getName())) {
			KubernetesClient kubernetesClient = getKubernetesClient(context.getEnvironment());

			if (OnKubernetesConditionSupport.isServerUp(kubernetesClient)) {
				return ConditionOutcome.match();
			}

			return ConditionOutcome.noMatch(
				message.because("Could not communicate with the Kubernetes server at: "
					+ kubernetesClient.getMasterUrl())
			);
		}

		return ConditionOutcome.noMatch(
			message.because("Unknown Conditional annotation using " +
				this.getClass().getSimpleName())
		);
	}

}
