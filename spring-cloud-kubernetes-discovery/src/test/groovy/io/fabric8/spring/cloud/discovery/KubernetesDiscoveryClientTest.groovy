package org.springframework.cloud.kubernetes.discovery

import io.fabric8.kubernetes.api.model.EndpointsBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.server.mock.KubernetesMockServer
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import spock.lang.Specification

class KubernetesDiscoveryClientTest extends Specification {

    private static KubernetesMockServer mockServer = new KubernetesMockServer()
    private static KubernetesClient mockClient


    def setupSpec() {
        mockServer.init()
        mockClient = mockServer.createClient()


        //Configure the kubernetes master url to point to the mock server
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl())
        System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true")
        System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false")
        System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false")
    }

    def cleanupSpec() {
        mockServer.destroy();
    }

    def "Should be able to handle service with single endpoint and address"() {
		def properties = new KubernetesDiscoveryProperties()
		given:

		Map<String, String> labels = new HashMap<>()
		labels.put(properties.springBootAppLabel, "true")

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/my-service").andReturn(200, new ServiceBuilder()

			.withNewMetadata()
				.withName("service")
				.withLabels(labels)
			.endMetadata()


			.build()).once()

        mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/my-service").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("my-service")
                .endMetadata()
                .addNewSubset()
                    .addNewAddress()
                        .withIp("ip1")
                    .endAddress()
                    .addNewPort("http",80,"TCP")
                .endSubset()
                .build()).once()



        DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties )
        when:
        List<ServiceInstance> instances = discoveryClient.getInstances("my-service")
        then:
        instances != null
        instances.size() == 1
        instances.find({s -> s.host == "ip1"})
    }



    def "Should be able to handle endpoints multiple addresses"() {
		def properties = new KubernetesDiscoveryProperties()
        given:
		Map<String, String> labels = new HashMap<>()
		labels.put(properties.springBootAppLabel, "true")

		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/my-service").andReturn(200, new ServiceBuilder()
			.withNewMetadata()
			.withName("my-service")
			.withLabels(labels)
			.endMetadata()

			.build()).once()


        mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/my-service").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("my-service")
		//			.withLabels(labels)
                .endMetadata()

                .addNewSubset()
                    .addNewAddress()
                        .withIp("ip1")
                    .endAddress()
                    .addNewAddress()
                        .withIp("ip2")
                    .endAddress()
                    .addNewPort("http",80,"TCP")
                .endSubset()
                .build()).once()

        DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(mockClient, properties)
        when:
        List<ServiceInstance> instances = discoveryClient.getInstances("my-service")
        then:
        instances != null
        instances.size() == 2
        instances.find({s -> s.host == "ip1"})
        instances.find({s -> s.host == "ip2"})

    }
}
