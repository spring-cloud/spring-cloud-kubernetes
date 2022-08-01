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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.net.URI;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
public class HttpBasedSecretsWatchChangeDetector extends SecretsWatcherChangeDetector {

	/**
	 * Annotation key for actuator port and path.
	 */
	public static String ANNOTATION_KEY = "boot.spring.io/actuator";

	private WebClient webClient;

	private KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient;

	public HttpBasedSecretsWatchChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor, WebClient webClient,
			KubernetesInformerReactiveDiscoveryClient k8sReactiveDiscoveryClient) {
		super(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider,
				k8SConfigurationProperties, threadPoolTaskExecutor);
		this.webClient = webClient;
		this.kubernetesReactiveDiscoveryClient = k8sReactiveDiscoveryClient;
	}

	@Override
	protected Mono<Void> triggerRefresh(V1Secret secret) {
		return refresh(secret.getMetadata()).then();
	}

	private void setActuatorUriFromAnnotation(UriComponentsBuilder actuatorUriBuilder, String metadataUri) {
		URI annotationUri = URI.create(metadataUri);
		actuatorUriBuilder.path(annotationUri.getPath() + "/refresh");

		// The URI may not contain a host so if that is the case the port in the URI will
		// be -1
		// The authority of the URI will be :<port> for example :9090, we just need the
		// 9090 in this case
		if (annotationUri.getPort() < 0) {
			if (annotationUri.getAuthority() != null) {
				actuatorUriBuilder.port(annotationUri.getAuthority().replaceFirst(":", ""));
			}
		}
		else {
			actuatorUriBuilder.port(annotationUri.getPort());
		}
	}

	private URI getActuatorUri(ServiceInstance si) {
		String metadataUri = si.getMetadata().getOrDefault(ANNOTATION_KEY, "");
		if (log.isDebugEnabled()) {
			log.debug("Metadata actuator uri is: " + metadataUri);
		}

		UriComponentsBuilder actuatorUriBuilder = UriComponentsBuilder.newInstance().scheme(si.getScheme())
				.host(si.getHost());

		if (!StringUtils.isEmpty(metadataUri)) {
			if (log.isDebugEnabled()) {
				log.debug("Found actuator URI in service instance metadata");
			}
			setActuatorUriFromAnnotation(actuatorUriBuilder, metadataUri);
		}
		else {
			Integer port = k8SConfigurationProperties.getActuatorPort() < 0 ? si.getPort()
					: k8SConfigurationProperties.getActuatorPort();
			actuatorUriBuilder = actuatorUriBuilder.path(k8SConfigurationProperties.getActuatorPath() + "/refresh")
					.port(port);
		}

		return actuatorUriBuilder.build().toUri();
	}

	protected Flux<ResponseEntity<Void>> refresh(V1ObjectMeta objectMeta) {

		return kubernetesReactiveDiscoveryClient.getInstances(objectMeta.getName()).flatMap(si -> {
			URI actuatorUri = getActuatorUri(si);
			if (log.isDebugEnabled()) {
				log.debug("Sending refresh request for " + objectMeta.getName() + " to URI " + actuatorUri.toString());
			}
			Mono<ResponseEntity<Void>> response = webClient.post().uri(actuatorUri).retrieve().toBodilessEntity()
					.doOnSuccess(re -> {
						if (log.isDebugEnabled()) {
							log.debug("Refresh sent to " + objectMeta.getName() + " at URI address " + actuatorUri
									+ " returned a " + re.getStatusCode().toString());
						}
					}).doOnError(t -> {
						log.warn("Refresh sent to " + objectMeta.getName() + " failed", t);
					});
			return response;
		});
	}

}
