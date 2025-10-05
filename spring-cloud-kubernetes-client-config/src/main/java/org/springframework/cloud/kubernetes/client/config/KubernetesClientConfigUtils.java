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

package org.springframework.cloud.kubernetes.client.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.ObjectUtils;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.getApplicationNamespace;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesBatchRead.strippedConfigMaps;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesBatchRead.strippedSecrets;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesSingleRead.strippedConfigMaps;
import static org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesSingleRead.strippedSecrets;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.processLabeledData;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.processNamedData;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientConfigUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesClientConfigUtils.class));

	private KubernetesClientConfigUtils() {
	}

	/**
	 * finds namespaces to be used for the event based reloading.
	 */
	public static Set<String> namespaces(KubernetesNamespaceProvider provider, ConfigReloadProperties properties,
			String target) {
		Set<String> namespaces = properties.namespaces();
		if (namespaces.isEmpty()) {
			namespaces = Set.of(getApplicationNamespace(null, target, provider));
		}
		LOG.debug("informer namespaces : " + namespaces);
		return namespaces;
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the config maps has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsByName(CoreV1Api client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, boolean includeDefaultProfileData,
			ReadType readType) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (readType.equals(ReadType.BATCH)) {
			LOG.debug(() -> "Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMaps(client, namespace);
		}
		else {
			LOG.debug(() -> "Will read individual configmaps in namespace : " + namespace + " with names : "
					+ sourceNames);
			strippedConfigMaps = strippedConfigMaps(client, namespace, sourceNames);
		}

		return processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false,
				includeDefaultProfileData);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the secrets has a single yaml/properties file
	 *     4. gather all the names of the secrets + decoded data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsByName(CoreV1Api client, String namespace, LinkedHashSet<String> sourceNames,
			Environment environment, boolean includeDefaultProfileData, ReadType readType) {

		List<StrippedSourceContainer> strippedSecrets;

		if (readType.equals(ReadType.BATCH)) {
			LOG.debug(() -> "Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecrets(client, namespace);
		}
		else {
			LOG.debug(
					() -> "Will read individual secrets in namespace : " + namespace + " with names : " + sourceNames);
			strippedSecrets = strippedSecrets(client, namespace, sourceNames);
		}

		return processNamedData(strippedSecrets, environment, sourceNames, namespace, false, includeDefaultProfileData);
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any from (2) has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsByLabels(CoreV1Api client, String namespace, Map<String, String> labels,
			Environment environment, ReadType readType) {

		List<StrippedSourceContainer> strippedConfigMaps;

		if (readType.equals(ReadType.BATCH)) {
			LOG.debug(() -> "Will read all configmaps in namespace : " + namespace);
			strippedConfigMaps = strippedConfigMaps(client, namespace);
		}
		else {
			LOG.debug(() -> "Will read individual configmaps in namespace : " + namespace + " with labels : " + labels);
			strippedConfigMaps = strippedConfigMaps(client, namespace, labels);
		}

		return processLabeledData(strippedConfigMaps, environment, labels, namespace, false);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any of the secrets from (2) has a single yaml/properties file
	 *     4. gather all the names of the secrets + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsByLabels(CoreV1Api client, String namespace, Map<String, String> labels,
			Environment environment, ReadType readType) {

		List<StrippedSourceContainer> strippedSecrets;

		if (readType.equals(ReadType.BATCH)) {
			LOG.debug(() -> "Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecrets(client, namespace);
		}
		else {
			LOG.debug(() -> "Will read individual secrets in namespace : " + namespace + " with labels : " + labels);
			strippedSecrets = strippedSecrets(client, namespace, labels);
		}

		return processLabeledData(strippedSecrets, environment, labels, namespace, false);
	}

	static List<StrippedSourceContainer> stripSecrets(List<V1Secret> secrets) {
		return secrets.stream()
			.map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(), secret.getMetadata().getName(),
					transform(secret.getData())))
			.toList();
	}

	static List<StrippedSourceContainer> stripConfigMaps(List<V1ConfigMap> configMaps) {
		return configMaps.stream()
			.map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
					configMap.getMetadata().getName(), configMap.getData()))
			.toList();
	}

	static void handleApiException(ApiException e, String sourceName) {
		if (e.getCode() == 404) {
			LOG.warn(() -> "source with name : " + sourceName + " not found. Ignoring");
		}
		else {
			throw new RuntimeException(e.getResponseBody(), e);
		}
	}

	private static Map<String, String> transform(Map<String, byte[]> in) {
		return ObjectUtils.isEmpty(in) ? Map.of()
				: in.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, en -> new String(en.getValue())));
	}

}
