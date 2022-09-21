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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.net.URI;
import java.util.function.Consumer;

import io.kubernetes.client.common.KubernetesObject;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.core.log.LogAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author wind57
 */
final class HttpRefreshTrigger implements RefreshTrigger {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class));

	private final KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient;

	private final ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	private final WebClient webClient;

	HttpRefreshTrigger(KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties, WebClient webClient) {
		this.kubernetesReactiveDiscoveryClient = kubernetesReactiveDiscoveryClient;
		this.k8SConfigurationProperties = k8SConfigurationProperties;
		this.webClient = webClient;
	}

	@Override
	public Mono<Void> triggerRefresh(KubernetesObject kubernetesObject, String appName) {

		return kubernetesReactiveDiscoveryClient.getInstances(appName).flatMap(si -> {
			URI actuatorUri = getActuatorUri(si, k8SConfigurationProperties.getActuatorPath(),
					k8SConfigurationProperties.getActuatorPort());
			LOG.debug(() -> "Sending refresh request for " + appName + " to URI " + actuatorUri);
			return webClient.post().uri(actuatorUri).retrieve().toBodilessEntity()
					.doOnSuccess(onSuccess(appName, actuatorUri)).doOnError(onError(appName));
		}).then();
	}

	private Consumer<ResponseEntity<Void>> onSuccess(String name, URI actuatorUri) {
		return re -> LOG.debug(() -> "Refresh sent to " + name + " at URI address " + actuatorUri + " returned a "
				+ re.getStatusCode());
	}

	private Consumer<Throwable> onError(String name) {
		return t -> LOG.warn(t, () -> "Refresh sent to " + name + " failed");
	}

	private URI getActuatorUri(ServiceInstance si, String actuatorPath, int actuatorPort) {
		String metadataUri = si.getMetadata().getOrDefault(ConfigurationWatcherConfigurationProperties.ANNOTATION_KEY,
				"");
		LOG.debug(() -> "Metadata actuator uri is: " + metadataUri);

		UriComponentsBuilder actuatorUriBuilder = UriComponentsBuilder.newInstance().scheme(si.getScheme())
				.host(si.getHost());

		if (StringUtils.hasText(metadataUri)) {
			LOG.debug(() -> "Found actuator URI in service instance metadata");
			setActuatorUriFromAnnotation(actuatorUriBuilder, metadataUri);
		}
		else {
			int port = actuatorPort < 0 ? si.getPort() : actuatorPort;
			actuatorUriBuilder = actuatorUriBuilder.path(actuatorPath + "/refresh").port(port);
		}

		return actuatorUriBuilder.build().toUri();
	}

	private void setActuatorUriFromAnnotation(UriComponentsBuilder actuatorUriBuilder, String metadataUri) {
		URI annotationUri = URI.create(metadataUri);
		actuatorUriBuilder.path(annotationUri.getPath() + "/refresh");

		// The URI may not contain a host so if that is the case the port in the URI will
		// be -1. The authority of the URI will be :<port> for example :9090, we just need
		// the 9090 in this case
		if (annotationUri.getPort() < 0) {
			if (annotationUri.getAuthority() != null) {
				actuatorUriBuilder.port(annotationUri.getAuthority().replaceFirst(":", ""));
			}
		}
		else {
			actuatorUriBuilder.port(annotationUri.getPort());
		}
	}

}
