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

package org.springframework.cloud.kubernetes.fabric8;

import java.time.Duration;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for Kubernetes.
 *
 * @author Ioannis Canellos
 * @author Eddú Meléndez
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesEnabled
@AutoConfigureAfter(KubernetesCommonsAutoConfiguration.class)
public class Fabric8AutoConfiguration {

	private static final Log LOG = LogFactory.getLog(Fabric8AutoConfiguration.class);

	private static <D> D or(D left, D right) {
		return left != null ? left : right;
	}

	private static Integer orDurationInt(Duration left, Integer right) {
		return left != null ? (int) left.toMillis() : right;
	}

	private static Long orDurationLong(Duration left, Long right) {
		return left != null ? left.toMillis() : right;
	}

	@Bean
	@ConditionalOnMissingBean(Config.class)
	public Config kubernetesClientConfig(KubernetesClientProperties kubernetesClientProperties) {
		Config base = Config.autoConfigure(null);
		Config properties = new ConfigBuilder(base)
				// Only set values that have been explicitly specified
				.withMasterUrl(or(kubernetesClientProperties.getMasterUrl(), base.getMasterUrl()))
				.withApiVersion(or(kubernetesClientProperties.getApiVersion(), base.getApiVersion()))
				.withNamespace(or(kubernetesClientProperties.getNamespace(), base.getNamespace()))
				.withUsername(or(kubernetesClientProperties.getUsername(), base.getUsername()))
				.withPassword(or(kubernetesClientProperties.getPassword(), base.getPassword()))

				.withOauthToken(or(kubernetesClientProperties.getOauthToken(), base.getOauthToken()))
				.withCaCertFile(or(kubernetesClientProperties.getCaCertFile(), base.getCaCertFile()))
				.withCaCertData(or(kubernetesClientProperties.getCaCertData(), base.getCaCertData()))

				.withClientKeyFile(or(kubernetesClientProperties.getClientKeyFile(), base.getClientKeyFile()))
				.withClientKeyData(or(kubernetesClientProperties.getClientKeyData(), base.getClientKeyData()))

				.withClientCertFile(or(kubernetesClientProperties.getClientCertFile(), base.getClientCertFile()))
				.withClientCertData(or(kubernetesClientProperties.getClientCertData(), base.getClientCertData()))

				// No magic is done for the properties below so we leave them as is.
				.withClientKeyAlgo(or(kubernetesClientProperties.getClientKeyAlgo(), base.getClientKeyAlgo()))
				.withClientKeyPassphrase(
						or(kubernetesClientProperties.getClientKeyPassphrase(), base.getClientKeyPassphrase()))
				.withConnectionTimeout(
						orDurationInt(kubernetesClientProperties.getConnectionTimeout(), base.getConnectionTimeout()))
				.withRequestTimeout(
						orDurationInt(kubernetesClientProperties.getRequestTimeout(), base.getRequestTimeout()))
				.withRollingTimeout(
						orDurationLong(kubernetesClientProperties.getRollingTimeout(), base.getRollingTimeout()))
				.withTrustCerts(or(kubernetesClientProperties.isTrustCerts(), base.isTrustCerts()))
				.withHttpProxy(or(kubernetesClientProperties.getHttpProxy(), base.getHttpProxy()))
				.withHttpsProxy(or(kubernetesClientProperties.getHttpsProxy(), base.getHttpsProxy()))
				.withProxyUsername(or(kubernetesClientProperties.getProxyUsername(), base.getProxyUsername()))
				.withProxyPassword(or(kubernetesClientProperties.getProxyPassword(), base.getProxyPassword()))
				.withNoProxy(or(kubernetesClientProperties.getNoProxy(), base.getNoProxy())).build();

		if (properties.getNamespace() == null || properties.getNamespace().isEmpty()) {
			LOG.warn("No namespace has been detected. Please specify "
					+ "KUBERNETES_NAMESPACE env var, or use a later kubernetes version (1.3 or later)");
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
	public Fabric8PodUtils kubernetesPodUtils(KubernetesClient client) {
		return new Fabric8PodUtils(client);
	}

}
