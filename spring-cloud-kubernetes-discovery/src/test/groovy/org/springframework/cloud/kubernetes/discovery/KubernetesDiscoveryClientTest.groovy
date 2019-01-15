/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.discovery

import io.fabric8.kubernetes.api.model.EndpointsBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.ServiceListBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.server.mock.KubernetesMockServer
import org.assertj.core.api.Assertions
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import spock.lang.Specification

import static org.assertj.core.api.Assertions.*

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

    def "getInstances should be able to handle endpoints single address"() {
        given:
        mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/endpoint").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("endpoint")
                .endMetadata()
                .addNewSubset()
                    .addNewAddress()
                        .withIp("ip1")
                    .endAddress()
                    .addNewPort("http",80,"TCP")
                .endSubset()
                .build()).once()
		and:
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, new ServiceBuilder()
			.withNewMetadata()
				.withName("endpoint")
			.withLabels(new HashMap<String, String>() {{
				put("l", "v")
			}})
			.endMetadata()
			.build()).once()

		and:
        DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(
			mockClient, new KubernetesDiscoveryProperties(), {client -> client.services()})

		when:
        List<ServiceInstance> instances = discoveryClient.getInstances("endpoint")

		then:
        instances != null
        instances.size() == 1
        instances.find({s -> s.host == "ip1"})
    }



    def "getInstances should be able to handle endpoints multiple addresses"() {
        given:
        mockServer.expect().get().withPath("/api/v1/namespaces/test/endpoints/endpoint").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("endpoint")
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

		and:
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services/endpoint").andReturn(200, new ServiceBuilder()
			.withNewMetadata()
				.withName("endpoint")
			.withLabels(new HashMap<String, String>() {{
				put("l", "v")
			}})
			.endMetadata()
			.build()).once()

		and:
        DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(
			mockClient, new KubernetesDiscoveryProperties(), {client -> client.services()})

		when:
        List<ServiceInstance> instances = discoveryClient.getInstances("endpoint")
        then:
        instances != null
        instances.size() == 2
        instances.find({s -> s.host == "ip1"})
        instances.find({s -> s.host == "ip2"})

    }

	def "getServices should return all services when no labels are applied to the client"() {
		given:
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services").andReturn(200, new ServiceListBuilder()
			.addNewItem()
				.withNewMetadata()
				.withName("s1")
				.withLabels(new HashMap<String, String>() {{
					put("label", "value")
				}})
				.endMetadata()
			.endItem()
		    .addNewItem()
				.withNewMetadata()
				.withName("s2")
				.withLabels(new HashMap<String, String>() {{
					put("label", "value")
					put("label2", "value2")
				}})
				.endMetadata()
		    .endItem()
			.addNewItem()
				.withNewMetadata()
				.withName("s3")
				.endMetadata()
			.endItem()
			.build()).once()

		and:
		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(
			mockClient, new KubernetesDiscoveryProperties(), {client -> client.services()})

		when:
		List<String> instances = discoveryClient.getServices()

		then:
		assertThat(instances).containsOnly("s1", "s2", "s3")
	}

	def "getServices should return only matching services when labels are applied to the client"() {
		given:
		// this is the URL that is created by the KubernetesClient when a a label named 'label'
		// with a value of 'value' is specified
		mockServer.expect().get().withPath("/api/v1/namespaces/test/services?labelSelector=label%3Dvalue").andReturn(200, new ServiceListBuilder()
			.addNewItem()
				.withNewMetadata()
				.withName("s1")
				.withLabels(new HashMap<String, String>() {{
					put("label", "value")
				}})
				.endMetadata()
			.endItem()
			.addNewItem()
				.withNewMetadata()
				.withName("s2")
				.withLabels(new HashMap<String, String>() {{
					put("label", "value")
					put("label2", "value2")
				}})
				.endMetadata()
			.endItem()
			.build()).once()

		and:
		DiscoveryClient discoveryClient = new KubernetesDiscoveryClient(
			mockClient,
			new KubernetesDiscoveryProperties(), {client -> client.services().withLabels(["label": "value"])})

		when:
		List<String> instances = discoveryClient.getServices()

		then:
		assertThat(instances).containsOnly("s1", "s2")
	}
}
