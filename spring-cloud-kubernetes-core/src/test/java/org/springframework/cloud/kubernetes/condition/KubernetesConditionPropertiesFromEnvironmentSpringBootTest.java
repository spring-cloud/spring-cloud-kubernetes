package org.springframework.cloud.kubernetes.condition;

import static org.junit.Assert.assertNotNull;
import static org.springframework.cloud.kubernetes.Constants.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.kubernetes.condition.App.K8SBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class)
@ContextConfiguration(initializers =
	KubernetesConditionPropertiesFromEnvironmentSpringBootTest.Initializer.class)
@DirtiesContext
public class KubernetesConditionPropertiesFromEnvironmentSpringBootTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	@Autowired(required = false)
	private K8SBean k8SBean;

	@BeforeClass
	public static void setUpBeforeClass() {
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
	}

	@Test
	public void anyK8sBeanShouldBeCreated() {
		assertNotNull(k8SBean);
	}


	public static class Initializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
			EnvironmentTestUtils
				.addEnvironment("mock", configurableApplicationContext.getEnvironment(),
					String.format(
						"%s.master-url=%s",
						SPRING_CLOUD_KUBERNETES_CLIENT_PROPS,
						server.getClient().getMasterUrl()
					),
					"spring.application.name=props-example"
				);
		}
	}
}
