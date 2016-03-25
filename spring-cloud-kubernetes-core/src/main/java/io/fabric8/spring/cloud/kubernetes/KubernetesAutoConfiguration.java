/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.fabric8.spring.cloud.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@EnableConfigurationProperties(KubernetesClientProperties.class)
public class KubernetesAutoConfiguration {

    @Autowired
    private KubernetesClientProperties properties;

    @Bean
    @ConditionalOnMissingBean(Config.class)
    public Config kubernetesClientConfig() {
        Config base = new Config();
        Config properites = new ConfigBuilder(base)
                //Only set values that have been explicitly specified
                .withMasterUrl(or(properties.getMasterUrl(), base.getMasterUrl()))
                .withMasterUrl(or(properties.getApiVersion(), base.getApiVersion()))
                .withMasterUrl(or(properties.getApiVersion(), base.getMasterUrl()))
                .withUsername(or(properties.getUsername(), base.getUsername()))
                .withPassword(or(properties.getPassword(), base.getPassword()))

                .withCaCertFile(or(properties.getCaCertFile(), base.getCaCertFile()))
                .withCaCertData(or(properties.getCaCertData(), base.getCaCertData()))

                .withClientKeyFile(or(properties.getClientKeyFile(), base.getClientKeyFile()))
                .withClientKeyData(or(properties.getClientKeyData(), base.getClientKeyData()))

                .withClientCertFile(or(properties.getClientCertFile(), base.getClientCertFile()))
                .withClientCertData(or(properties.getClientCertData(), base.getClientCertData()))

                //No magic is done for the properties below so we leave them as is.
                .withClientKeyAlgo(or(properties.getClientKeyAlgo(), base.getClientKeyAlgo()))
                .withClientKeyPassphrase(or(properties.getClientKeyPassphrase(), base.getClientKeyPassphrase()))
                .withConnectionTimeout(or(properties.getConnectionTimeout(), base.getConnectionTimeout()))
                .withRequestTimeout(or(properties.getRequestTimeout(), base.getRequestTimeout()))
                .withRollingTimeout(or(properties.getRollingTimeout(), base.getRollingTimeout()))
                .withTrustCerts(or(properties.isTrustCerts(), base.isTrustCerts()))
                .build();

        if (!base.equals(properites)) {
            System.out.println("Objects different");
        }

        return properites;
    }

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient(Config config) {
        return new DefaultKubernetesClient(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public KubernetesHealthIndicator kubernetesHealthIndicator(KubernetesClient client) {
        return new KubernetesHealthIndicator(client);
    }

    private static <D> D or(D dis, D dat) {
        if (dis != null) {
            return dis;
        } else {
            return dat;
        }
    }
}
