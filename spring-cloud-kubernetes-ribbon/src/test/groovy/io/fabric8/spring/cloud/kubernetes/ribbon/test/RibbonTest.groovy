/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.ribbon.test

import io.fabric8.kubernetes.api.model.EndpointsBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.server.mock.KubernetesMockServer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.kubernetes.ribbon.KubernetesRibbonClientConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor
import org.springframework.cloud.netflix.ribbon.RibbonClient
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

@EnableAutoConfiguration
@ContextConfiguration(classes=[TestApplication.class])
@SpringBootTest(properties=
        [
                "spring.application.name=testapp",
                "spring.cloud.kubernetes.client.namespace=testns",
                "spring.cloud.kubernetes.client.trustCerts=true",
                "spring.cloud.kubernetes.config.namespace=testns"
        ])
@RibbonClient(name = "testapp", configuration = KubernetesRibbonClientConfiguration.class)
class RibbonTest extends Specification {

    private static KubernetesMockServer mockServer = new KubernetesMockServer()
    private static KubernetesMockServer mockEndpointA = new KubernetesMockServer(false)
    private static KubernetesMockServer mockEndpointB = new KubernetesMockServer(false)
    private static KubernetesClient mockClient;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    LoadBalancerInterceptor interceptor;

    def setupSpec() {
        mockServer.init()
        mockEndpointA.init()
        mockEndpointB.init()
        mockClient = mockServer.createClient()

        //Configure the kubernetes master url to point to the mock server
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl())
        System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true")
        System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false")
        System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false")

        //Configured
        mockServer.expect().get().withPath("/api/v1/namespaces/testns/endpoints/testapp").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("testapp-a")
                .endMetadata()
                .addNewSubset()
                    .addNewAddress().withIp(mockEndpointA.getServer().hostName).endAddress()
                    .addNewPort("http", mockEndpointA.getServer().port, "http")
                .endSubset()
                .addNewSubset()
                .addNewAddress().withIp(mockEndpointB.getServer().hostName).endAddress()
                    .addNewPort("http", mockEndpointB.getServer().port, "http")
                .endSubset()
                .build()).always()

        mockEndpointA.expect().get().withPath("/greeting").andReturn(200, "Hello from A").always()
        mockEndpointB.expect().get().withPath("/greeting").andReturn(200, "Hello from B").always()
    }

    def cleanupSpec() {
        mockServer.destroy()
        mockEndpointA.destroy()
        mockEndpointB.destroy()
    }


    def "A ribbon rest template should round robin over the available kubernetes endpoints"() {
        given:
            List<String> grettings = new ArrayList<>()
        when:
            for (int i = 0; i < 2 ; i++) {
                grettings.add(restTemplate.getForObject("http://testapp/greeting", String.class))
            }
        then:
            grettings.contains("Hello from A")
            grettings.contains("Hello from B")
    }
}
