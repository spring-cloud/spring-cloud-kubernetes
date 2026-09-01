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

package org.springframework.cloud.kubernetes.configuration.watcher.ha;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;
import io.kubernetes.client.openapi.models.V1Lease;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

/**
 * Lease-backed persistent store for watcher HA state.
 *
 * <p>
 * All methods in this class are expected to be called only by the watcher instance that
 * currently holds leadership. Follower instances must not read or write HA state through
 * this store.
 *
 * @author wind57
 */
final class LeaseConfigurationWatcherStateStore implements ConfigurationWatcherStateStore {

	private static final LogAccessor LOG = new LogAccessor(LeaseConfigurationWatcherStateStore.class);

	private static final String CONFIGMAP_ANNOTATION = "spring.cloud.kubernetes.configuration.watcher/configmap-resource-version";

	private static final String SECRET_ANNOTATION = "spring.cloud.kubernetes.configuration.watcher/secret-resource-version";

	private final CoordinationV1Api api;

	private final String leaseName;

	private final String leaseNamespace;

	LeaseConfigurationWatcherStateStore(CoordinationV1Api api, ConfigurationWatcherHaProperties properties) {
		this.api = api;
		this.leaseName = properties.getLeaseName();
		String configuredLeaseNamespace = properties.getLeaseNamespace();
		this.leaseNamespace = StringUtils.hasText(configuredLeaseNamespace) ? configuredLeaseNamespace : "default";
	}

	@Override
	public ConfigurationWatcherState readOrCreate() {

		LOG.debug(() -> "Reading lease with name: " + leaseName + " in namespace: " + leaseNamespace);

		try {
			V1Lease lease = api.readNamespacedLease(leaseName, leaseNamespace).execute();
			Map<String, String> annotations = existingAnnotations(lease);
			return new ConfigurationWatcherState(parseResourceVersions(annotations.get(CONFIGMAP_ANNOTATION)),
					parseResourceVersions(annotations.get(SECRET_ANNOTATION)));
		}
		catch (ApiException e) {
			if (e.getCode() == 404) {
				createLease(leaseName, leaseNamespace);
				return ConfigurationWatcherState.EMPTY;
			}
			throw new IllegalStateException(
					"Failed to read watcher HA lease '" + leaseName + "' in namespace '" + leaseNamespace + "'", e);
		}
	}

	@Override
	public void writeConfigMapResourceVersion(String namespace, String resourceVersion) {
		writeResourceVersion(CONFIGMAP_ANNOTATION, namespace, resourceVersion);
	}

	@Override
	public void writeSecretResourceVersion(String namespace, String resourceVersion) {
		writeResourceVersion(SECRET_ANNOTATION, namespace, resourceVersion);
	}

	private void writeResourceVersion(String annotation, String namespace, String resourceVersion) {
		try {

			LOG.debug(() -> "Updating lease with name: " + leaseName + " in namespace: " + leaseNamespace);

			V1Lease currentLease = api.readNamespacedLease(leaseName, leaseNamespace).execute();
			V1Lease updatedLease = updatedLease(currentLease, annotation, namespace, resourceVersion);
			api.replaceNamespacedLease(leaseName, leaseNamespace, updatedLease).execute();
		}
		catch (ApiException e) {
			LOG.error(e, () -> "Failed to write to the lease because : " + e.getResponseBody());
			throw new IllegalStateException(
					"Failed to write watcher HA lease '" + leaseName + "' in namespace '" + leaseNamespace + "'", e);
		}
	}

	private void createLease(String leaseName, String leaseNamespace) {
		try {
			LOG.info(() -> "Creating watcher HA lease with name : " + leaseName + " in namespace : " + leaseNamespace);
			api.createNamespacedLease(leaseNamespace, newLease(leaseName, leaseNamespace)).execute();
		}
		catch (ApiException e) {
			LOG.error(e, () -> "Failed to create watcher HA lease. " + e.getResponseBody());
			throw new IllegalStateException(
					"Failed to create watcher HA lease '" + leaseName + "' in namespace '" + leaseNamespace + "'", e);
		}
	}

	private V1Lease updatedLease(V1Lease lease, String annotation, String namespace, String resourceVersion) {
		V1ObjectMeta metadata = lease.getMetadata();
		Map<String, String> currentLeaseAnnotations = existingAnnotations(lease);
		Map<String, String> updatedAnnotations = new HashMap<>(currentLeaseAnnotations);
		Map<String, String> resourceVersions = parseResourceVersions(updatedAnnotations.get(annotation));
		resourceVersions.put(namespace, resourceVersion);
		updatedAnnotations.put(annotation, serializeResourceVersions(resourceVersions));
		metadata.setAnnotations(updatedAnnotations);
		return lease;
	}

	private V1Lease newLease(String leaseName, String leaseNamespace) {
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(leaseName);
		metadata.setNamespace(leaseNamespace);
		return new V1Lease().metadata(metadata);
	}

	private static Map<String, String> existingAnnotations(V1Lease lease) {
		if (lease.getMetadata() == null || lease.getMetadata().getAnnotations() == null) {
			LOG.warn(() -> "Lease has no annotations");
			return Map.of();
		}
		return lease.getMetadata().getAnnotations();
	}

	private static String serializeResourceVersions(Map<String, String> resourceVersions) {
		return resourceVersions.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining(","));
	}

	private static Map<String, String> parseResourceVersions(String serializedResourceVersions) {
		if (!StringUtils.hasText(serializedResourceVersions)) {
			return new HashMap<>(); // mutable on purpose
		}

		Map<String, String> resourceVersions = new LinkedHashMap<>();
		for (String entry : serializedResourceVersions.split(",")) {
			String[] keyValue = entry.split("=", 2);
			if (keyValue.length != 2) {
				throw new IllegalStateException("Invalid ConfigMap resource version entry: " + entry);
			}
			resourceVersions.put(keyValue[0], keyValue[1]);
		}
		return resourceVersions;
	}

}
