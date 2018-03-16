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

package org.springframework.cloud.kubernetes;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@EnableConfigurationProperties(KubernetesClientProperties.class)
public class KubernetesAutoConfiguration {

    private static final Log LOG = LogFactory.getLog(KubernetesAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(Config.class)
    public Config kubernetesClientConfig(KubernetesClientProperties kubernetesClientProperties) {
        Config base = Config.autoConfigure(null);
        Config properties = new ConfigBuilder(base)
                //Only set values that have been explicitly specified
                .withMasterUrl(or(kubernetesClientProperties.getMasterUrl(), base.getMasterUrl()))
                .withApiVersion(or(kubernetesClientProperties.getApiVersion(), base.getApiVersion()))
                .withNamespace(or(kubernetesClientProperties.getNamespace(), base.getNamespace()))
                .withUsername(or(kubernetesClientProperties.getUsername(), base.getUsername()))
                .withPassword(or(kubernetesClientProperties.getPassword(), base.getPassword()))

                .withCaCertFile(or(kubernetesClientProperties.getCaCertFile(), base.getCaCertFile()))
                .withCaCertData(or(kubernetesClientProperties.getCaCertData(), base.getCaCertData()))

                .withClientKeyFile(or(kubernetesClientProperties.getClientKeyFile(), base.getClientKeyFile()))
                .withClientKeyData(or(kubernetesClientProperties.getClientKeyData(), base.getClientKeyData()))

                .withClientCertFile(or(kubernetesClientProperties.getClientCertFile(), base.getClientCertFile()))
                .withClientCertData(or(kubernetesClientProperties.getClientCertData(), base.getClientCertData()))

                //No magic is done for the properties below so we leave them as is.
                .withClientKeyAlgo(or(kubernetesClientProperties.getClientKeyAlgo(), base.getClientKeyAlgo()))
                .withClientKeyPassphrase(or(kubernetesClientProperties.getClientKeyPassphrase(), base.getClientKeyPassphrase()))
                .withConnectionTimeout(or(kubernetesClientProperties.getConnectionTimeout(), base.getConnectionTimeout()))
                .withRequestTimeout(or(kubernetesClientProperties.getRequestTimeout(), base.getRequestTimeout()))
                .withRollingTimeout(or(kubernetesClientProperties.getRollingTimeout(), base.getRollingTimeout()))
                .withTrustCerts(or(kubernetesClientProperties.isTrustCerts(), base.isTrustCerts()))
                .build();

        if (properties.getNamespace() == null || properties.getNamespace().isEmpty()) {
            LOG.warn("No namespace has been detected. Please specify KUBERNETES_NAMESPACE env var, or use a later kubernetes version (1.3 or later)");
        }
        return properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient(Config config) {
        return new DefaultKubernetesClient(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public StandardPodUtils kubernetesPodUtils(KubernetesClient client) {
        return new StandardPodUtils(client);
    }

    @Configuration
    @ConditionalOnClass(HealthIndicator.class)
    protected static class KubernetesActuatorConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public KubernetesHealthIndicator kubernetesHealthIndicator(PodUtils podUtils) {
            return new KubernetesHealthIndicator(podUtils);
        }
    }

    private static <D> D or(D dis, D dat) {
        if (dis != null) {
            return dis;
        } else {
            return dat;
        }
    }
}
