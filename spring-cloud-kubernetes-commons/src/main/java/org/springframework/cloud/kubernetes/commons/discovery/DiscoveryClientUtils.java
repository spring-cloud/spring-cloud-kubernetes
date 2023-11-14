/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.keysWithPrefix;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SERVICE_TYPE;

/**
 * @author wind57
 */
public final class DiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(DiscoveryClientUtils.class));

	private DiscoveryClientUtils() {
		throw new AssertionError("no instance provided");
	}

	/**
	 * This adds the following metadata. <pre>
	 *     - labels (if requested)
	 *     - annotations (if requested)
	 *     - ports (if requested)
	 *     - namespace
	 *     - service type
	 * </pre>
	 */
	public static Map<String, String> serviceInstanceMetadata(Map<String, Integer> portsData,
			ServiceMetadata serviceMetadata, KubernetesDiscoveryProperties properties) {
		Map<String, String> result = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = properties.metadata();
		if (metadataProps.addLabels()) {
			Map<String, String> labelMetadata = keysWithPrefix(serviceMetadata.labels(), metadataProps.labelsPrefix());
			LOG.debug(() -> "Adding labels metadata: " + labelMetadata + " for serviceId: " + serviceMetadata.name());
			result.putAll(labelMetadata);
		}
		if (metadataProps.addAnnotations()) {
			Map<String, String> annotationMetadata = keysWithPrefix(serviceMetadata.annotations(),
					metadataProps.annotationsPrefix());
			LOG.debug(() -> "Adding annotations metadata: " + annotationMetadata + " for serviceId: "
					+ serviceMetadata.name());
			result.putAll(annotationMetadata);
		}

		if (metadataProps.addPorts()) {
			Map<String, String> portsDataValueAsString = portsData.entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, en -> Integer.toString(en.getValue())));
			Map<String, String> portMetadata = keysWithPrefix(portsDataValueAsString,
					properties.metadata().portsPrefix());
			if (!portMetadata.isEmpty()) {
				LOG.debug(() -> "Adding port metadata: " + portMetadata + " for serviceId : " + serviceMetadata.name());
			}
			result.putAll(portMetadata);
		}

		result.put(NAMESPACE_METADATA_KEY, serviceMetadata.namespace());
		result.put(SERVICE_TYPE, serviceMetadata.type());
		return result;
	}

	public static ServicePortNameAndNumber endpointsPort(Map<String, Integer> existingPorts,
			ServiceMetadata serviceMetadata, KubernetesDiscoveryProperties properties) {

		if (existingPorts.isEmpty()) {
			LOG.debug(() -> "no ports found for service : " + serviceMetadata.name() + ", will return zero");
			return new ServicePortNameAndNumber(0, "http");
		}

		if (existingPorts.size() == 1) {
			Map.Entry<String, Integer> single = existingPorts.entrySet().iterator().next();
			LOG.debug(() -> "endpoint ports has a single entry, using port : " + single.getValue());
			return new ServicePortNameAndNumber(single.getValue(), single.getKey());
		}

		else {

			Optional<ServicePortNameAndNumber> portData;
			String primaryPortName = primaryPortName(properties, serviceMetadata.labels(), serviceMetadata.name());

			portData = fromMap(existingPorts, primaryPortName, "found primary-port-name (with value: '"
					+ primaryPortName + "') via properties or service labels to match port");
			if (portData.isPresent()) {
				return portData.get();
			}

			portData = fromMap(existingPorts, HTTPS, "found primary-port-name via 'https' to match port");
			if (portData.isPresent()) {
				return portData.get();
			}

			portData = fromMap(existingPorts, HTTP, "found primary-port-name via 'http' to match port");
			if (portData.isPresent()) {
				return portData.get();
			}

			logWarnings();
			Map.Entry<String, Integer> first = existingPorts.entrySet().iterator().next();
			return new ServicePortNameAndNumber(first.getValue(), first.getKey());

		}
	}

	public static ServiceInstance serviceInstance(@Nullable ServicePortSecureResolver servicePortSecureResolver,
			ServiceMetadata serviceMetadata, Supplier<InstanceIdHostPodName> instanceIdAndHost,
			Function<String, PodLabelsAndAnnotations> podLabelsAndMetadata, ServicePortNameAndNumber portData,
			Map<String, String> serviceInstanceMetadata, KubernetesDiscoveryProperties properties) {

		InstanceIdHostPodName data = instanceIdAndHost.get();

		boolean secured;
		if (servicePortSecureResolver == null) {
			secured = false;
		}
		else {
			secured = servicePortSecureResolver.resolve(new ServicePortSecureResolver.Input(portData,
					serviceMetadata.name(), serviceMetadata.labels(), serviceMetadata.annotations()));
		}

		Map<String, Map<String, String>> podMetadata = podMetadata(data.podName(), serviceInstanceMetadata, properties,
				podLabelsAndMetadata);

		return new DefaultKubernetesServiceInstance(data.instanceId(), serviceMetadata.name(), data.host(),
				portData.portNumber(), serviceInstanceMetadata, secured, serviceMetadata.namespace(), null,
				podMetadata);
	}

	/**
	 * take primary-port-name from service label "PRIMARY_PORT_NAME_LABEL_KEY" if it
	 * exists, otherwise from KubernetesDiscoveryProperties if it exists, otherwise null.
	 */
	static String primaryPortName(KubernetesDiscoveryProperties properties, Map<String, String> serviceLabels,
			String serviceId) {
		String primaryPortNameFromProperties = properties.primaryPortName();

		// the value from labels takes precedence over the one from properties
		String primaryPortName = Optional
				.ofNullable(Optional.ofNullable(serviceLabels).orElse(Map.of()).get(PRIMARY_PORT_NAME_LABEL_KEY))
				.orElse(primaryPortNameFromProperties);

		if (primaryPortName == null) {
			LOG.debug(
					() -> "did not find a primary-port-name in neither properties nor service labels for service with ID : "
							+ serviceId);
			return null;
		}

		LOG.debug(() -> "will use primaryPortName : " + primaryPortName + " for service with ID = " + serviceId);
		return primaryPortName;
	}

	static Map<String, Map<String, String>> podMetadata(String podName, Map<String, String> serviceMetadata,
			KubernetesDiscoveryProperties properties, Function<String, PodLabelsAndAnnotations> podLabelsAndMetadata) {
		if (!EXTERNAL_NAME.equals(serviceMetadata.get(SERVICE_TYPE))) {
			if (properties.metadata().addPodLabels() || properties.metadata().addPodAnnotations()) {

				LOG.debug(() -> "Pod labels/annotations were requested");

				if (podName != null) {
					LOG.debug(() -> "getting labels/annotation for pod: " + podName);
					PodLabelsAndAnnotations both = podLabelsAndMetadata.apply(podName);
					Map<String, Map<String, String>> result = new HashMap<>();
					if (properties.metadata().addPodLabels() && !both.labels().isEmpty()) {
						result.put("labels", both.labels());
					}

					if (properties.metadata().addPodAnnotations() && !both.annotations().isEmpty()) {
						result.put("annotations", both.annotations());
					}

					LOG.debug(() -> "adding podMetadata : " + result + " from pod : " + podName);
					return result;
				}

			}
		}

		return Map.of();
	}

	private static Optional<ServicePortNameAndNumber> fromMap(Map<String, Integer> existingPorts, String key,
			String message) {
		Integer fromPrimaryPortName = existingPorts.get(key);
		if (fromPrimaryPortName == null) {
			LOG.debug(() -> "not " + message);
			return Optional.empty();
		}
		else {
			LOG.debug(() -> message + " : " + fromPrimaryPortName);
			return Optional.of(new ServicePortNameAndNumber(fromPrimaryPortName, key));
		}
	}

	private static void logWarnings() {
		LOG.warn(() -> """
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour.""");
	}

}
