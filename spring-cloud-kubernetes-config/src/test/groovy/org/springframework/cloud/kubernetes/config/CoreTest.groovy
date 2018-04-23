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

package org.springframework.cloud.kubernetes.config

import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import groovy.util.logging.Slf4j

@Slf4j
@ContextConfiguration(classes=[TestApplication.class])
@SpringBootTest(properties=[
	"spring.application.name=testapp",
	"spring.cloud.kubernetes.client.namespace=testns",
	"spring.cloud.kubernetes.client.trustCerts=true",
	"spring.cloud.kubernetes.config.namespace=testns",
	"spring.cloud.kubernetes.secrets.enableApi=true"
])
@EnableConfigurationProperties
class CoreTest extends Specification {

    private static KubernetesMockServer mockServer = new KubernetesMockServer()
    private static KubernetesClient mockClient

    @Autowired
    Environment environment

    @Autowired(required = false)
    Config config

    @Autowired(required = false)
    KubernetesClient client

    def setupSpec() {
        mockServer.init()
        mockClient = mockServer.createClient()

        mockServer.expect().get()
             .withPath("/api/v1/namespaces/testns/configmaps/testapp")
             .andReturn(
                 200,
                 new ConfigMapBuilder()
                    .withData([
                        'spring.kubernetes.test.value': 'value1'])
                    .build())
             .always()
        mockServer.expect().get()
            .withPath("/api/v1/namespaces/testns/secrets/testapp")
            .andReturn(
                200,
                new SecretBuilder()
                    .withData([
                        'amq.pwd': 'MWYyZDFlMmU2N2Rm',
                        'amq.usr': 'YWRtaW4K'
                    ])
                    .build())
            .always()

        //Configure the kubernetes master url to point to the mock server
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl())
        System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true")
        System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false")
        System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false")
    }

    def cleanupSpec() {
        mockServer.destroy();
    }

    def "Kubernetes client config bean should be present"() {
        expect:
            config != null
    }

    def "Kubernetes client config bean should be configurable via system properties"() {
        expect:
            config.getMasterUrl().equals(mockClient.getConfiguration().getMasterUrl())
            config.getNamespace().equals("testns")
            config.trustCerts
    }

    def "Kubernetes client bean should be present"() {
        expect:
            client != null
    }

    def "Kubernetes client should be configured from system properties"() {
        expect:
            client.getConfiguration().getMasterUrl().equals(mockClient.getConfiguration().getMasterUrl())
    }

    def "properties should be read from config map"() {
        expect:
            environment.getProperty("spring.kubernetes.test.value").equals("value1")
    }

    def "properties should be read from secrets"() {
        expect:
            environment.getProperty("amq.pwd").equals("1f2d1e2e67df")
            environment.getProperty("amq.usr").equals('admin');
    }
}
