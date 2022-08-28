package org.springframework.cloud.kubernetes.configuration.watcher;

import io.kubernetes.client.common.KubernetesObject;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.core.log.LogAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Consumer;

final class HttpRefreshTrigger implements RefreshTrigger {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(KubernetesClientEventBasedSecretsChangeDetector.class));

	private final KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient;

	private final ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	private final WebClient webClient;

	public HttpRefreshTrigger(KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient,
							  ConfigurationWatcherConfigurationProperties k8SConfigurationProperties, WebClient webClient) {
		this.kubernetesReactiveDiscoveryClient = kubernetesReactiveDiscoveryClient;
		this.k8SConfigurationProperties = k8SConfigurationProperties;
		this.webClient = webClient;
	}

	@Override
	public Mono<Void> triggerRefresh(KubernetesObject kubernetesObject) {

		String name = kubernetesObject.getMetadata().getName();

		return kubernetesReactiveDiscoveryClient.getInstances(name).flatMap(si -> {
			URI actuatorUri = getActuatorUri(si,
				k8SConfigurationProperties.getActuatorPath(), k8SConfigurationProperties.getActuatorPort());
			LOG.debug(() -> "Sending refresh request for " + name + " to URI " + actuatorUri);
			return webClient.post().uri(actuatorUri).retrieve().toBodilessEntity()
				.doOnSuccess(onSuccess(name, actuatorUri))
				.doOnError(onError(name));
		}).then();
	}

	private Consumer<ResponseEntity<Void>> onSuccess(String name, URI actuatorUri) {
		return re -> LOG.debug(() -> "Refresh sent to " + name + " at URI address " + actuatorUri
			+ " returned a " + re.getStatusCode());
	}

	private Consumer<Throwable> onError(String name) {
		return t -> LOG.warn(t, () -> "Refresh sent to " + name + " failed");
	}

	private URI getActuatorUri(ServiceInstance si, String actuatorPath, int actuatorPort) {
		String metadataUri = si.getMetadata().getOrDefault(ConfigurationWatcherConfigurationProperties.ANNOTATION_KEY, "");
		LOG.debug(() -> "Metadata actuator uri is: " + metadataUri);

		UriComponentsBuilder actuatorUriBuilder = UriComponentsBuilder.newInstance().scheme(si.getScheme())
			.host(si.getHost());

		if (!StringUtils.hasText(metadataUri)) {
			LOG.debug(() -> "Found actuator URI in service instance metadata");
			setActuatorUriFromAnnotation(actuatorUriBuilder, metadataUri);
		}
		else {
			int port = actuatorPort < 0 ? si.getPort() : actuatorPort;
			actuatorUriBuilder = actuatorUriBuilder.path(actuatorPath + "/refresh")
				.port(port);
		}

		return actuatorUriBuilder.build().toUri();
	}

	private static void setActuatorUriFromAnnotation(UriComponentsBuilder actuatorUriBuilder, String metadataUri) {
		URI annotationUri = URI.create(metadataUri);
		actuatorUriBuilder.path(annotationUri.getPath() + "/refresh");

		// The URI may not contain a host so if that is the case the port in the URI will
		// be -1. The authority of the URI will be :<port> for example :9090, we just need the
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
}
