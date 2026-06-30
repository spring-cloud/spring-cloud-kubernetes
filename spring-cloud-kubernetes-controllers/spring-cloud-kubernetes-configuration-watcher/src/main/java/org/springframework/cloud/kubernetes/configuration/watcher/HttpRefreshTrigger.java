/*
 * Copyright 2013-present the original author or authors.
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

import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.core.log.LogAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.RefreshStrategy.SHUTDOWN;
import static org.springframework.cloud.kubernetes.configuration.watcher.WatcherUtil.matchesByLabels;

/**
 * @author wind57
 */
final class HttpRefreshTrigger implements RefreshTrigger {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(HttpRefreshTrigger.class));

	private final ReactiveDiscoveryClient reactiveDiscoveryClient;

	/**
	 * <pre>
	 * Keep a reference to the properties bean instead of copying individual values into
	 * final fields. The configuration watcher can be configured from a ConfigMap and can
	 * refresh itself, so ConfigurationWatcherConfigurationProperties may be rebound at
	 * runtime.
	 *
	 * RefreshScope annotation is not an option here:
	 * - class-based refresh proxies require subclassing, but HttpRefreshTrigger is final
	 * - interface-based refresh proxies would have to implement RefreshTrigger, but
	 *   RefreshTrigger is sealed
	 * For these reasons, we always read the current values from the properties bean.
	 * </pre>
	 */
	private final ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	private final WebClient webClient;

	HttpRefreshTrigger(ReactiveDiscoveryClient reactiveDiscoveryClient,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties, WebClient webClient) {
		this.reactiveDiscoveryClient = reactiveDiscoveryClient;
		this.k8SConfigurationProperties = k8SConfigurationProperties;
		this.webClient = webClient;
	}

	@Override
	public Mono<Void> triggerRefresh(KubernetesSource kubernetesSource) {
		if (!kubernetesSource.serviceLabels().isEmpty()) {
			LOG.info(() -> "Using service labels for discovery : " + kubernetesSource.serviceLabels());
			return reactiveDiscoveryClient.getServices()
				.flatMap(reactiveDiscoveryClient::getInstances)
				.filter(serviceInstance -> matchesByLabels(serviceInstance, kubernetesSource.serviceLabels()))
				.flatMap(serviceInstance -> refresh(serviceInstance.getServiceId(), serviceInstance))
				.then();
		}

		LOG.info(() -> "Using service names for discovery : " + kubernetesSource.serviceNames());
		return Flux.fromIterable(kubernetesSource.serviceNames())
			.flatMap(serviceName -> reactiveDiscoveryClient.getInstances(serviceName)
				.flatMap(serviceInstance -> refresh(serviceName, serviceInstance)))
			.then();
	}

	private Mono<ResponseEntity<Void>> refresh(String serviceName, ServiceInstance serviceInstance) {
		URI actuatorUri = getActuatorUri(serviceInstance, k8SConfigurationProperties.getActuatorPath(),
				k8SConfigurationProperties.getActuatorPort());
		LOG.debug(() -> "Sending refresh request for " + serviceName + " to URI " + actuatorUri);
		return webClient.post()
			.uri(actuatorUri)
			.retrieve()
			.toBodilessEntity()
			.doOnSuccess(onSuccess(serviceName, actuatorUri))
			.doOnError(onError(serviceName));
	}

	private Consumer<ResponseEntity<Void>> onSuccess(String name, URI actuatorUri) {
		return re -> LOG.debug(() -> "Refresh sent to " + name + " at URI address " + actuatorUri + " returned a "
				+ re.getStatusCode());
	}

	private Consumer<Throwable> onError(String name) {
		return t -> LOG.warn(t, () -> "Refresh sent to " + name + " failed");
	}

	private URI getActuatorUri(ServiceInstance si, String actuatorPath, int actuatorPort) {
		String metadataUri = si.getMetadata()
			.getOrDefault(ConfigurationWatcherConfigurationProperties.ANNOTATION_KEY, "");
		LOG.debug(() -> "Metadata actuator uri is: " + metadataUri);

		UriComponentsBuilder actuatorUriBuilder = UriComponentsBuilder.newInstance()
			.scheme(si.getScheme())
			.host(si.getHost());

		if (StringUtils.hasText(metadataUri)) {
			LOG.debug(() -> "Found actuator URI in service instance metadata");
			setActuatorUriFromAnnotation(actuatorUriBuilder, metadataUri);
		}
		else {
			int port = actuatorPort < 0 ? si.getPort() : actuatorPort;
			actuatorUriBuilder = actuatorUriBuilder.path(actuatorPath + getRefreshStrategyEndpoint()).port(port);
		}

		return actuatorUriBuilder.build().toUri();
	}

	private void setActuatorUriFromAnnotation(UriComponentsBuilder actuatorUriBuilder, String metadataUri) {
		URI annotationUri = URI.create(metadataUri);
		actuatorUriBuilder.path(annotationUri.getPath() + getRefreshStrategyEndpoint());

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

	private String getRefreshStrategyEndpoint() {
		if (k8SConfigurationProperties.getRefreshStrategy() == SHUTDOWN) {
			return "/shutdown";
		}
		return "/refresh";
	}

}
