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

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesClientPropertiesTests {

	@Test
	void testDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			KubernetesClientProperties properties = context.getBean(KubernetesClientProperties.class);
			assertThat(properties).isNotNull();
			assertThat(properties.trustCerts()).isNull();
			assertThat(properties.masterUrl()).isNull();
			assertThat(properties.apiVersion()).isNull();
			assertThat(properties.namespace()).isNull();
			assertThat(properties.caCertFile()).isNull();
			assertThat(properties.caCertData()).isNull();
			assertThat(properties.clientCertFile()).isNull();
			assertThat(properties.clientCertData()).isNull();
			assertThat(properties.clientKeyFile()).isNull();
			assertThat(properties.clientKeyData()).isNull();
			assertThat(properties.clientKeyAlgo()).isNull();
			assertThat(properties.clientKeyPassphrase()).isNull();
			assertThat(properties.username()).isNull();
			assertThat(properties.password()).isNull();
			assertThat(properties.watchReconnectInterval()).isNull();
			assertThat(properties.watchReconnectLimit()).isNull();
			assertThat(properties.connectionTimeout()).isNull();
			assertThat(properties.requestTimeout()).isNull();
			assertThat(properties.rollingTimeout()).isNull();
			assertThat(properties.loggingInterval()).isNull();
			assertThat(properties.httpProxy()).isNull();
			assertThat(properties.httpsProxy()).isNull();
			assertThat(properties.proxyUsername()).isNull();
			assertThat(properties.proxyPassword()).isNull();
			assertThat(properties.oauthToken()).isNull();
			assertThat(properties.noProxy()).isNull();
			assertThat(properties.serviceAccountNamespacePath())
					.isEqualTo("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
			assertThat(properties.userAgent()).isEqualTo("Spring-Cloud-Kubernetes-Application");
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(
				"spring.cloud.kubernetes.client.trust-certs=true",
				"spring.cloud.kubernetes.client.master-url=master-url", "spring.cloud.kubernetes.client.api-version=1",
				"spring.cloud.kubernetes.client.namespace=namespace",
				"spring.cloud.kubernetes.client.ca-cert-file=ca-cert-file",
				"spring.cloud.kubernetes.client.ca-cert-data=ca-cert-data",
				"spring.cloud.kubernetes.client.client-cert-file=client-cert-file",
				"spring.cloud.kubernetes.client.client-cert-data=client-cert-data",
				"spring.cloud.kubernetes.client.client-key-file=client-key-file",
				"spring.cloud.kubernetes.client.client-key-data=client-key-data",
				"spring.cloud.kubernetes.client.client-key-algo=client-key-algo",
				"spring.cloud.kubernetes.client.client-key-passphrase=client-key-passphrase",
				"spring.cloud.kubernetes.client.username=username", "spring.cloud.kubernetes.client.password=password",
				"spring.cloud.kubernetes.client.watch-reconnect-interval=200ms",
				"spring.cloud.kubernetes.client.watch-reconnect-limit=300ms",
				"spring.cloud.kubernetes.client.connection-timeout=400ms",
				"spring.cloud.kubernetes.client.request-timeout=500ms",
				"spring.cloud.kubernetes.client.rolling-timeout=600ms",
				"spring.cloud.kubernetes.client.logging-interval=700ms",
				"spring.cloud.kubernetes.client.http-proxy=http-proxy",
				"spring.cloud.kubernetes.client.https-proxy=https-proxy",
				"spring.cloud.kubernetes.client.proxy-username=proxy-username",
				"spring.cloud.kubernetes.client.proxy-password=proxy-password",
				"spring.cloud.kubernetes.client.oauth-token=oauth-token",
				"spring.cloud.kubernetes.client.no-proxy[0]=a", "spring.cloud.kubernetes.client.no-proxy[1]=b",
				"spring.cloud.kubernetes.client.service-account-namespace-path=path",
				"spring.cloud.kubernetes.client.user-agent=user-agent").run(context -> {
					KubernetesClientProperties properties = context.getBean(KubernetesClientProperties.class);
					assertThat(properties).isNotNull();
					assertThat(properties.trustCerts()).isTrue();
					assertThat(properties.masterUrl()).isEqualTo("master-url");
					assertThat(properties.apiVersion()).isEqualTo("1");
					assertThat(properties.namespace()).isEqualTo("namespace");
					assertThat(properties.caCertFile()).isEqualTo("ca-cert-file");
					assertThat(properties.caCertData()).isEqualTo("ca-cert-data");
					assertThat(properties.clientCertFile()).isEqualTo("client-cert-file");
					assertThat(properties.clientCertData()).isEqualTo("client-cert-data");
					assertThat(properties.clientKeyFile()).isEqualTo("client-key-file");
					assertThat(properties.clientKeyData()).isEqualTo("client-key-data");
					assertThat(properties.clientKeyAlgo()).isEqualTo("client-key-algo");
					assertThat(properties.clientKeyPassphrase()).isEqualTo("client-key-passphrase");
					assertThat(properties.username()).isEqualTo("username");
					assertThat(properties.password()).isEqualTo("password");
					assertThat(properties.watchReconnectInterval()).isEqualTo(Duration.ofMillis(200));
					assertThat(properties.watchReconnectLimit()).isEqualTo(Duration.ofMillis(300));
					assertThat(properties.connectionTimeout()).isEqualTo(Duration.ofMillis(400));
					assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(500));
					assertThat(properties.rollingTimeout()).isEqualTo(Duration.ofMillis(600));
					assertThat(properties.loggingInterval()).isEqualTo(Duration.ofMillis(700));
					assertThat(properties.httpProxy()).isEqualTo("http-proxy");
					assertThat(properties.httpsProxy()).isEqualTo("https-proxy");
					assertThat(properties.proxyUsername()).isEqualTo("proxy-username");
					assertThat(properties.proxyPassword()).isEqualTo("proxy-password");
					assertThat(properties.oauthToken()).isEqualTo("oauth-token");
					assertThat(properties.noProxy().length).isEqualTo(2);
					assertThat(properties.noProxy()[0]).isEqualTo("a");
					assertThat(properties.noProxy()[1]).isEqualTo("b");
					assertThat(properties.serviceAccountNamespacePath()).isEqualTo("path");
					assertThat(properties.userAgent()).isEqualTo("user-agent");
				});
	}

	@Test
	void testCopyWithNamespaceConstructor() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(
				"spring.cloud.kubernetes.client.trust-certs=true",
				"spring.cloud.kubernetes.client.master-url=master-url", "spring.cloud.kubernetes.client.api-version=1",
				"spring.cloud.kubernetes.client.namespace=namespace",
				"spring.cloud.kubernetes.client.ca-cert-file=ca-cert-file",
				"spring.cloud.kubernetes.client.ca-cert-data=ca-cert-data",
				"spring.cloud.kubernetes.client.client-cert-file=client-cert-file",
				"spring.cloud.kubernetes.client.client-cert-data=client-cert-data",
				"spring.cloud.kubernetes.client.client-key-file=client-key-file",
				"spring.cloud.kubernetes.client.client-key-data=client-key-data",
				"spring.cloud.kubernetes.client.client-key-algo=client-key-algo",
				"spring.cloud.kubernetes.client.client-key-passphrase=client-key-passphrase",
				"spring.cloud.kubernetes.client.username=username", "spring.cloud.kubernetes.client.password=password",
				"spring.cloud.kubernetes.client.watch-reconnect-interval=200ms",
				"spring.cloud.kubernetes.client.watch-reconnect-limit=300ms",
				"spring.cloud.kubernetes.client.connection-timeout=400ms",
				"spring.cloud.kubernetes.client.request-timeout=500ms",
				"spring.cloud.kubernetes.client.rolling-timeout=600ms",
				"spring.cloud.kubernetes.client.logging-interval=700ms",
				"spring.cloud.kubernetes.client.http-proxy=http-proxy",
				"spring.cloud.kubernetes.client.https-proxy=https-proxy",
				"spring.cloud.kubernetes.client.proxy-username=proxy-username",
				"spring.cloud.kubernetes.client.proxy-password=proxy-password",
				"spring.cloud.kubernetes.client.oauth-token=oauth-token",
				"spring.cloud.kubernetes.client.no-proxy[0]=a", "spring.cloud.kubernetes.client.no-proxy[1]=b",
				"spring.cloud.kubernetes.client.service-account-namespace-path=path",
				"spring.cloud.kubernetes.client.user-agent=user-agent").run(context -> {
					KubernetesClientProperties properties = context.getBean(KubernetesClientProperties.class)
							.withNamespace("non-default");
					assertThat(properties).isNotNull();
					assertThat(properties.trustCerts()).isTrue();
					assertThat(properties.masterUrl()).isEqualTo("master-url");
					assertThat(properties.apiVersion()).isEqualTo("1");
					assertThat(properties.namespace()).isEqualTo("non-default");
					assertThat(properties.caCertFile()).isEqualTo("ca-cert-file");
					assertThat(properties.caCertData()).isEqualTo("ca-cert-data");
					assertThat(properties.clientCertFile()).isEqualTo("client-cert-file");
					assertThat(properties.clientCertData()).isEqualTo("client-cert-data");
					assertThat(properties.clientKeyFile()).isEqualTo("client-key-file");
					assertThat(properties.clientKeyData()).isEqualTo("client-key-data");
					assertThat(properties.clientKeyAlgo()).isEqualTo("client-key-algo");
					assertThat(properties.clientKeyPassphrase()).isEqualTo("client-key-passphrase");
					assertThat(properties.username()).isEqualTo("username");
					assertThat(properties.password()).isEqualTo("password");
					assertThat(properties.watchReconnectInterval()).isEqualTo(Duration.ofMillis(200));
					assertThat(properties.watchReconnectLimit()).isEqualTo(Duration.ofMillis(300));
					assertThat(properties.connectionTimeout()).isEqualTo(Duration.ofMillis(400));
					assertThat(properties.requestTimeout()).isEqualTo(Duration.ofMillis(500));
					assertThat(properties.rollingTimeout()).isEqualTo(Duration.ofMillis(600));
					assertThat(properties.loggingInterval()).isEqualTo(Duration.ofMillis(700));
					assertThat(properties.httpProxy()).isEqualTo("http-proxy");
					assertThat(properties.httpsProxy()).isEqualTo("https-proxy");
					assertThat(properties.proxyUsername()).isEqualTo("proxy-username");
					assertThat(properties.proxyPassword()).isEqualTo("proxy-password");
					assertThat(properties.oauthToken()).isEqualTo("oauth-token");
					assertThat(properties.noProxy().length).isEqualTo(2);
					assertThat(properties.noProxy()[0]).isEqualTo("a");
					assertThat(properties.noProxy()[1]).isEqualTo("b");
					assertThat(properties.serviceAccountNamespacePath()).isEqualTo("path");
					assertThat(properties.userAgent()).isEqualTo("user-agent");
				});
	}

	@EnableConfigurationProperties(KubernetesClientProperties.class)
	@Configuration
	static class Config {

	}

}
