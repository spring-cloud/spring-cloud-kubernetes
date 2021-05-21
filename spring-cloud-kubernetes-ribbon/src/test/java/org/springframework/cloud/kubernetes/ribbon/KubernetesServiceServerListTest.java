package org.springframework.cloud.kubernetes.ribbon;

import com.netflix.loadbalancer.Server;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileName: KubernetesServiceServerListTest
 * Author:   decylus
 * Date:     2021/5/21 1:28 pm
 * Description:
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class,
	properties = { "spring.application.name=testapp",
		"spring.cloud.kubernetes.client.namespace=testns",
		"spring.cloud.kubernetes.client.trustCerts=true",
		"spring.cloud.kubernetes.config.namespace=testns",
		"spring.cloud.kubernetes.enabled=true",
		"spring.cloud.kubernetes.discovery.enabled=true",
		"spring.cloud.kubernetes.ribbon.enabled=true",
		"spring.cloud.kubernetes.ribbon.mode=SERVICE",
		"spring.cloud.kubernetes.ribbon.clusterDomain=test.com"})
@EnableAutoConfiguration
@EnableDiscoveryClient
public class KubernetesServiceServerListTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@BeforeClass
	public static void setUpBefore(){
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
			mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
			"false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");


		// Configured, mock a service with 2 ports, named 'otherPort50002' and 'expectedPort50001'
		server.expect().get().withPath("/api/v1/namespaces/testns/services/testapp")
			.andReturn(200, new ServiceBuilder().withNewMetadata().withName("testapp")
				.withNamespace("testns").endMetadata().withNewSpec()
				.addToSelector("app", "testapp-a")
				.addNewPort().withName("otherPort50002")
				.withPort(50002)
				.withTargetPort(
					new IntOrString(50002))
				.withProtocol("TCP").endPort()
				.addNewPort().withName("expectedPort50001")
				.withPort(50001)
				.withTargetPort(
					new IntOrString(50001))
				.withProtocol("TCP").endPort()

				.endSpec().build())
			.always();

	}

	@Test
	public void testGetServicesServerList() throws Exception{
		//mock class KubernetesServicesServerList
		KubernetesServicesServerList servicesServerList = new KubernetesServicesServerList(mockClient, new KubernetesRibbonProperties());
		Class<KubernetesServerList> clazz = KubernetesServerList.class;
		Field serviceIdField = clazz.getDeclaredField("serviceId");
		serviceIdField.setAccessible(true);
		serviceIdField.set(servicesServerList, "testapp");
		Field namespaceIdField = clazz.getDeclaredField("namespace");
		namespaceIdField.setAccessible(true);
		namespaceIdField.set(servicesServerList, "testns");

		//this field will filled by ribbon in property 'ribbon.PortName'
		//means we need all request send to the only port which is set in 'ribbon.PortName'
		Field portNameIdField = clazz.getDeclaredField("portName");
		portNameIdField.setAccessible(true);
		portNameIdField.set(servicesServerList, "expectedPort50001");

		//when we call getUpdatedListOfServers, we excepted only 1 port return;
		List<Server> list = servicesServerList.getUpdatedListOfServers();
		assertThat(list).hasSize(1);
		assertThat(list.get(0).getPort()).isEqualTo(50001);

		//try this test in the current main branch, there would be 2 server with both port 50001 and 50002,
		//that is not we expect

	}
}
