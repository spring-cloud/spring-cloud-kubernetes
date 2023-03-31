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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Bootstrap config for Kubernetes discovery config client.
 *
 * @author Zhanwei Wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.cloud.config.discovery.enabled")
@Import({ Fabric8AutoConfiguration.class, KubernetesDiscoveryClientAutoConfiguration.class })
@EnableConfigurationProperties({ KubernetesDiscoveryProperties.class, KubernetesClientProperties.class })
public class KubernetesDiscoveryClientConfigClientBootstrapConfiguration {

}
