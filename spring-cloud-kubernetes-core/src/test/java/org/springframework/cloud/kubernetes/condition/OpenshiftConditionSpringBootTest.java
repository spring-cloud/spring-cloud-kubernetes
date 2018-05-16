package org.springframework.cloud.kubernetes.condition;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE,
	classes = App.class,
	properties = { "spring.application.name=vanilla-k8s-example"}
)
@DirtiesContext
public class OpenshiftConditionSpringBootTest {

	@ClassRule
	public static OpenShiftServer server = new OpenShiftServer();

	@Autowired(required = false)
	private App.AnyK8sBean anyK8sBean;

	@Autowired(required = false)
	private App.OnlyVanillaK8sBean onlyVanillaK8sBean;

	@Autowired(required = false)
	private App.OpenshiftBean openshiftBean;

	@BeforeClass
	public static void setUpBeforeClass() {
		OpenShiftClient mockClient = server.getOpenshiftClient();

		//Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
			mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
	}

	@Test
	public void anyK8sBeanShouldBeCreated() {
		assertNotNull(anyK8sBean);
	}

	@Test
	public void onlyVanillaK8sBeanShouldNotBeCreated() {
		assertNull(onlyVanillaK8sBean);
	}

	@Test
	public void openshiftBeanShouldNotBeCreated() {
		assertNotNull(openshiftBean);
	}
}
