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

package io.fabric8.spring.cloud.kubernetes.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListFilter;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.spring.cloud.discovery.KubernetesDiscoveryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

import static com.netflix.client.config.CommonClientConfigKey.DeploymentContextBasedVipAddresses;
import static com.netflix.client.config.CommonClientConfigKey.EnableZoneAffinity;

@Configuration
public class KubernetesRibbonClientConfiguration {

    public KubernetesRibbonClientConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    public ServerList<?> ribbonServerList(KubernetesClient client, IClientConfig config) {
        KubernetesServerList serverList = new KubernetesServerList(client);
        serverList.initWithNiwsConfig(config);
        return serverList;
    }

}
