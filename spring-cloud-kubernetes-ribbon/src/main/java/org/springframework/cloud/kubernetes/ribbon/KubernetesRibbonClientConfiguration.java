/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes version of a Ribbon client configuration.
 *
 * @author Ioannis Canellos
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KubernetesRibbonProperties.class)
public class KubernetesRibbonClientConfiguration {

	public KubernetesRibbonClientConfiguration() {
	}

	@Bean
	@ConditionalOnMissingBean
	public ServerList<?> ribbonServerList(KubernetesClient client, IClientConfig config,
			KubernetesRibbonProperties properties) {
		KubernetesServerList serverList;
		if (properties.getMode() == KubernetesRibbonMode.SERVICE) {
			serverList = new KubernetesServicesServerList(client, properties);
		}
		else {
			serverList = new KubernetesEndpointsServerList(client, properties);
		}
		serverList.initWithNiwsConfig(config);
		return serverList;
	}

}
