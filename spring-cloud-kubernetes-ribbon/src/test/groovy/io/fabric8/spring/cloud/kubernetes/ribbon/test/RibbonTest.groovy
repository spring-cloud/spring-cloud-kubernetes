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

package io.fabric8.spring.cloud.kubernetes.ribbon.test

import io.fabric8.kubernetes.api.model.EndpointsBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.server.mock.KubernetesMockServer
import io.fabric8.spring.cloud.kubernetes.ribbon.KubernetesRibbonClientConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor
import org.springframework.cloud.netflix.ribbon.RibbonClient
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

@EnableAutoConfiguration
@SpringApplicationConfiguration(TestApplication.class)
@IntegrationTest(
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

        //Configured
        mockServer.expect().get().withPath("/api/v1/namespaces/testns/endpoints/testapp").andReturn(200, new EndpointsBuilder()
                .withNewMetadata()
                    .withName("testapp-a")
                .endMetadata()
                .addNewSubset()
                    .addNewAddresse().withIp(mockEndpointA.getServer().hostName).endAddresse()
                    .addNewPort("http", mockEndpointA.getServer().port, "http")
                .endSubset()
                .addNewSubset()
                .addNewAddresse().withIp(mockEndpointB.getServer().hostName).endAddresse()
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
