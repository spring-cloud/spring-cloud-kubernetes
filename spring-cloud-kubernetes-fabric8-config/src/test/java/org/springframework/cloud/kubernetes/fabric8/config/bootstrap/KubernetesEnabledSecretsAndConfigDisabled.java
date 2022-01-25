package org.springframework.cloud.kubernetes.fabric8.config.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.Application;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
	properties = { "spring.cloud.kubernetes.secrets.enabled=false",
		"spring.cloud.kubernetes.config.enabled=false" })
class KubernetesEnabledSecretsAndConfigDisabled {

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void secretsOnlyPresent() {
		assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
		assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
	}

}
