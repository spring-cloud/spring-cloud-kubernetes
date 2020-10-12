/*
 * Copyright 2013-2020 the original author or authors.
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

/**
 * Kubernetes client properties.
 *
 * @author Ioannis Canellos
 */
@ConfigurationProperties("spring.cloud.kubernetes.client")
public class KubernetesClientProperties {

	/**
	 * Default path for namespace file.
	 */
	public static final String SERVICE_ACCOUNT_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

	private Boolean trustCerts;

	private String masterUrl;

	private String apiVersion;

	private String namespace;

	private String caCertFile;

	private String caCertData;

	private String clientCertFile;

	private String clientCertData;

	private String clientKeyFile;

	private String clientKeyData;

	private String clientKeyAlgo;

	private String clientKeyPassphrase;

	private String username;

	private String password;

	private Duration watchReconnectInterval;

	private Duration watchReconnectLimit;

	private Duration connectionTimeout;

	private Duration requestTimeout;

	private Duration rollingTimeout;

	private Duration loggingInterval;

	private String httpProxy;

	private String httpsProxy;

	private String proxyUsername;

	private String proxyPassword;

	private String[] noProxy;

	private String serviceAccountNamespacePath = SERVICE_ACCOUNT_NAMESPACE_PATH;

	public String getServiceAccountNamespacePath() {
		return serviceAccountNamespacePath;
	}

	public void setServiceAccountNamespacePath(String serviceAccountNamespacePath) {
		this.serviceAccountNamespacePath = serviceAccountNamespacePath;
	}

	public String getClientCertData() {
		return this.clientCertData;
	}

	public void setClientCertData(String clientCertData) {
		this.clientCertData = clientCertData;
	}

	public Boolean isTrustCerts() {
		return this.trustCerts;
	}

	public String getMasterUrl() {
		return this.masterUrl;
	}

	public void setMasterUrl(String masterUrl) {
		this.masterUrl = masterUrl;
	}

	public String getApiVersion() {
		return this.apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	public String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getCaCertFile() {
		return this.caCertFile;
	}

	public void setCaCertFile(String caCertFile) {
		this.caCertFile = caCertFile;
	}

	public String getCaCertData() {
		return this.caCertData;
	}

	public void setCaCertData(String caCertData) {
		this.caCertData = caCertData;
	}

	public String getClientCertFile() {
		return this.clientCertFile;
	}

	public void setClientCertFile(String clientCertFile) {
		this.clientCertFile = clientCertFile;
	}

	public String getClientKeyFile() {
		return this.clientKeyFile;
	}

	public void setClientKeyFile(String clientKeyFile) {
		this.clientKeyFile = clientKeyFile;
	}

	public String getClientKeyData() {
		return this.clientKeyData;
	}

	public void setClientKeyData(String clientKeyData) {
		this.clientKeyData = clientKeyData;
	}

	public String getClientKeyAlgo() {
		return this.clientKeyAlgo;
	}

	public void setClientKeyAlgo(String clientKeyAlgo) {
		this.clientKeyAlgo = clientKeyAlgo;
	}

	public String getClientKeyPassphrase() {
		return this.clientKeyPassphrase;
	}

	public void setClientKeyPassphrase(String clientKeyPassphrase) {
		this.clientKeyPassphrase = clientKeyPassphrase;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Duration getWatchReconnectInterval() {
		return this.watchReconnectInterval;
	}

	public void setWatchReconnectInterval(Duration watchReconnectInterval) {
		this.watchReconnectInterval = watchReconnectInterval;
	}

	public Duration getWatchReconnectLimit() {
		return this.watchReconnectLimit;
	}

	public void setWatchReconnectLimit(Duration watchReconnectLimit) {
		this.watchReconnectLimit = watchReconnectLimit;
	}

	public Duration getConnectionTimeout() {
		return this.connectionTimeout;
	}

	public void setConnectionTimeout(Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Duration getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Duration getRollingTimeout() {
		return this.rollingTimeout;
	}

	public void setRollingTimeout(Duration rollingTimeout) {
		this.rollingTimeout = rollingTimeout;
	}

	public Duration getLoggingInterval() {
		return this.loggingInterval;
	}

	public void setLoggingInterval(Duration loggingInterval) {
		this.loggingInterval = loggingInterval;
	}

	public Boolean getTrustCerts() {
		return this.trustCerts;
	}

	public void setTrustCerts(Boolean trustCerts) {
		this.trustCerts = trustCerts;
	}

	public String getHttpProxy() {
		return this.httpProxy;
	}

	public void setHttpProxy(String httpProxy) {
		this.httpProxy = httpProxy;
	}

	public String getHttpsProxy() {
		return this.httpsProxy;
	}

	public void setHttpsProxy(String httpsProxy) {
		this.httpsProxy = httpsProxy;
	}

	public String getProxyUsername() {
		return this.proxyUsername;
	}

	public void setProxyUsername(String proxyUsername) {
		this.proxyUsername = proxyUsername;
	}

	public String getProxyPassword() {
		return this.proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public String[] getNoProxy() {
		return this.noProxy;
	}

	public void setNoProxy(String[] noProxy) {
		this.noProxy = noProxy;
	}

}
