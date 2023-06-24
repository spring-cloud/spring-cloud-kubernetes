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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
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
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureAfter(KubernetesCommonsAutoConfiguration.class)
public class Fabric8AutoConfiguration {

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
		ConfigBuilder builder = new ConfigBuilder(base)
				// Only set values that have been explicitly specified
				.withMasterUrl(or(kubernetesClientProperties.masterUrl(), base.getMasterUrl()))
				.withApiVersion(or(kubernetesClientProperties.apiVersion(), base.getApiVersion()))
				.withNamespace(or(kubernetesClientProperties.namespace(), base.getNamespace()))
				.withUsername(or(kubernetesClientProperties.username(), base.getUsername()))
				.withPassword(or(kubernetesClientProperties.password(), base.getPassword()))

				.withOauthToken(or(kubernetesClientProperties.oauthToken(), base.getOauthToken()))
				.withCaCertFile(or(kubernetesClientProperties.caCertFile(), base.getCaCertFile()))
				.withCaCertData(or(kubernetesClientProperties.caCertData(), base.getCaCertData()))

				.withClientKeyFile(or(kubernetesClientProperties.clientKeyFile(), base.getClientKeyFile()))
				.withClientKeyData(or(kubernetesClientProperties.clientKeyData(), base.getClientKeyData()))

				.withClientCertFile(or(kubernetesClientProperties.clientCertFile(), base.getClientCertFile()))
				.withClientCertData(or(kubernetesClientProperties.clientCertData(), base.getClientCertData()))

				// No magic is done for the properties below so we leave them as is.
				.withClientKeyAlgo(or(kubernetesClientProperties.clientKeyAlgo(), base.getClientKeyAlgo()))
				.withClientKeyPassphrase(
						or(kubernetesClientProperties.clientKeyPassphrase(), base.getClientKeyPassphrase()))
				.withConnectionTimeout(
						orDurationInt(kubernetesClientProperties.connectionTimeout(), base.getConnectionTimeout()))
				.withRequestTimeout(
						orDurationInt(kubernetesClientProperties.requestTimeout(), base.getRequestTimeout()))
				.withTrustCerts(or(kubernetesClientProperties.trustCerts(), base.isTrustCerts()))
				.withHttpProxy(or(kubernetesClientProperties.httpProxy(), base.getHttpProxy()))
				.withHttpsProxy(or(kubernetesClientProperties.httpsProxy(), base.getHttpsProxy()))
				.withProxyUsername(or(kubernetesClientProperties.proxyUsername(), base.getProxyUsername()))
				.withProxyPassword(or(kubernetesClientProperties.proxyPassword(), base.getProxyPassword()))
				.withNoProxy(or(kubernetesClientProperties.noProxy(), base.getNoProxy()))
				// Disable the built-in retry functionality since Spring Cloud Kubernetes
				// provides it
				// See https://github.com/fabric8io/kubernetes-client/issues/4863
				.withRequestRetryBackoffLimit(0);

		String userAgent = or(base.getUserAgent(), KubernetesClientProperties.DEFAULT_USER_AGENT);
		if (!kubernetesClientProperties.userAgent().equals(KubernetesClientProperties.DEFAULT_USER_AGENT)) {
			userAgent = kubernetesClientProperties.userAgent();
		}

		return builder.withUserAgent(userAgent).build();

	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesClient kubernetesClient(Config config) {
		return new KubernetesClientBuilder().withConfig(config).build();
	}

	@Bean
	@ConditionalOnMissingBean
	public Fabric8PodUtils kubernetesPodUtils(KubernetesClient client) {
		return new Fabric8PodUtils(client);
	}

}
