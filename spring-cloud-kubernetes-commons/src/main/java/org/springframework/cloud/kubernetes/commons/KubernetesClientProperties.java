/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static org.springframework.cloud.kubernetes.commons.KubernetesClientProperties.PREFIX;

/**
 * Kubernetes client properties.
 *
 * @author Ioannis Canellos
 */
@ConfigurationProperties(PREFIX)
public record KubernetesClientProperties(Boolean trustCerts, String masterUrl, String apiVersion, String namespace,
		String caCertFile, String caCertData, String clientCertFile, String clientCertData, String clientKeyFile,
		String clientKeyData, String clientKeyAlgo, String clientKeyPassphrase, String username, String password,
		Duration watchReconnectInterval, Duration watchReconnectLimit, Duration connectionTimeout,
		Duration requestTimeout, @Deprecated(forRemoval = true) Duration rollingTimeout, Duration loggingInterval,
		String httpProxy, String httpsProxy, String proxyUsername, String proxyPassword, String oauthToken,
		String[] noProxy, @DefaultValue(SERVICE_ACCOUNT_NAMESPACE_PATH) String serviceAccountNamespacePath,
		@DefaultValue(DEFAULT_USER_AGENT) String userAgent) {

	/**
	 * Configuration properties prefix.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.client";

	/**
	 * Default user-agent for kubernetes client.
	 */
	public static final String DEFAULT_USER_AGENT = "Spring-Cloud-Kubernetes-Application";

	/**
	 * Default path for namespace file.
	 */
	public static final String SERVICE_ACCOUNT_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

	/**
	 * copy constructor that only changes the namespace.
	 */
	public KubernetesClientProperties withNamespace(String namespace) {
		return new KubernetesClientProperties(this.trustCerts(), this.masterUrl(), this.apiVersion(), namespace,
				this.caCertFile(), this.caCertData(), this.clientCertFile(), this.clientCertData(),
				this.clientKeyFile(), this.clientKeyData(), this.clientKeyAlgo(), this.clientKeyPassphrase(),
				this.username(), this.password(), this.watchReconnectInterval(), this.watchReconnectLimit(),
				this.connectionTimeout(), this.requestTimeout(), this.rollingTimeout(), this.loggingInterval(),
				this.httpProxy(), this.httpsProxy(), this.proxyUsername(), this.proxyPassword(), this.oauthToken(),
				this.noProxy(), this.serviceAccountNamespacePath(), this.userAgent());
	}

}
