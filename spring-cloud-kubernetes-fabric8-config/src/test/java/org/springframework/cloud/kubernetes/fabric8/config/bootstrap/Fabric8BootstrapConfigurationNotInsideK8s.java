package org.springframework.cloud.kubernetes.fabric8.config.bootstrap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.Application;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
// effect, meaning when it is disabled, no property source bean is present
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
@Nested
class Fabric8BootstrapConfigurationNotInsideK8s {

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void bothMissing() {
		assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
		assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
	}

}
