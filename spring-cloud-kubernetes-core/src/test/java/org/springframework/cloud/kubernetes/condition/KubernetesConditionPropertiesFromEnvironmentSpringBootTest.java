package org.springframework.cloud.kubernetes.condition;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.cloud.kubernetes.KubernetesClientProperties.SPRING_CLOUD_KUBERNETES_CLIENT_PROPS;

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
import org.springframework.cloud.kubernetes.condition.App.K8SVersion18OrNewerBean;
import org.springframework.cloud.kubernetes.condition.App.K8SVersionExactly18Bean;
import org.springframework.cloud.kubernetes.condition.App.K8SVersionOlderThan18Bean;
import org.springframework.cloud.kubernetes.condition.App.OpenshiftBean;
import org.springframework.cloud.kubernetes.condition.App.OpenshiftVersion18OrNewerBean;
import org.springframework.cloud.kubernetes.condition.App.OpenshiftVersionOlderThan111Bean;
import org.springframework.cloud.kubernetes.condition.App.OpenshiftVersionOlderThan18Bean;
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

	private static final int SERVER_MINOR_VERSION = 10;
	private static final int SERVER_MAJOR_VERSION = 1;

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	@Autowired(required = false)
	private K8SBean k8SBean;

	@Autowired(required = false)
	private K8SVersion18OrNewerBean k8SVersion18OrNewerBean;

	@Autowired(required = false)
	private K8SVersionExactly18Bean k8SVersionExactly18Bean;

	@Autowired(required = false)
	private K8SVersionOlderThan18Bean k8SVersionOlderThan18Bean;

	@Autowired(required = false)
	private OpenshiftBean openshiftBean;

	@Autowired(required = false)
	private OpenshiftVersion18OrNewerBean openshiftVersion18OrNewerBean;

	@Autowired(required = false)
	private OpenshiftVersionOlderThan111Bean openshiftVersionOlderThan111Bean;

	@Autowired(required = false)
	private OpenshiftVersionOlderThan18Bean openshiftVersionOlderThan18Bean;

	@BeforeClass
	public static void setUpBeforeClass() {
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");

		server.expect().withPath("/version").andReturn(
			200, VersionInfoUtil.create(SERVER_MAJOR_VERSION, SERVER_MINOR_VERSION)).always();
	}

	@Test
	public void k8sBeanShouldBeCreated() {
		assertNotNull(k8SBean);
	}

	@Test
	public void k8SVersion18OrNewerBeanShouldBeCreated() {
		assertNotNull(k8SVersion18OrNewerBean);
	}

	@Test
	public void k8SVersionExactly18BeanShouldNotBeCreated() {
		assertNull(k8SVersionExactly18Bean);
	}

	@Test
	public void k8SVersionOlderThan18BeanShouldNotBeCreated() {
		assertNull(k8SVersionOlderThan18Bean);
	}

	@Test
	public void openshiftBeanShouldNotBeCreated() {
		assertNull(openshiftBean);
	}

	@Test
	public void openshiftVersion18OrNewerBeanShouldNotBeCreated() {
		assertNull(openshiftVersion18OrNewerBean);
	}

	@Test
	public void openshiftVersionLessThan111BeanShouldNotBeCreated() {
		assertNull(openshiftVersionOlderThan111Bean);
	}

	@Test
	public void openshiftVersionLessThan18BeanShouldNotBeCreated() {
		assertNull(openshiftVersionOlderThan18Bean);
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
