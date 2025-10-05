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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.processLabeledData;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.processNamedData;
import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.getApplicationNamespace;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesBatchRead.strippedConfigMaps;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesBatchRead.strippedSecrets;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesSingleRead.strippedConfigMaps;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8SourcesSingleRead.strippedSecrets;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class Fabric8ConfigUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ConfigUtils.class));

	private Fabric8ConfigUtils() {
	}

	/**
	 * finds namespaces to be used for the event based reloading.
	 */
	public static Set<String> namespaces(KubernetesClient client, KubernetesNamespaceProvider provider,
			ConfigReloadProperties properties, String target) {
		Set<String> namespaces = properties.namespaces();
		if (namespaces.isEmpty()) {
			namespaces = Set.of(getApplicationNamespace(client, null, target, provider));
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
	static MultipleSourcesContainer configMapsByName(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, ReadType readType) {

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

		return processNamedData(strippedConfigMaps, environment, sourceNames, namespace, false, true);
	}

	/**
	 * <pre>
	 *     1. read all secrets in the provided namespace
	 *     2. from the above, filter the ones that we care about (by name)
	 *     3. see if any of the secrets has a single yaml/properties file
	 *     4. gather all the names of the secrets + decoded data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer secretsByName(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames, Environment environment, ReadType readType) {

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

		return processNamedData(strippedSecrets, environment, sourceNames, namespace, true, true);
	}

	/**
	 * <pre>
	 *     1. read all config maps in the provided namespace
	 *     2. from the above, filter the ones that we care about (filter by labels)
	 *     3. see if any from (2) has a single yaml/properties file
	 *     4. gather all the names of the config maps + data they hold
	 * </pre>
	 */
	static MultipleSourcesContainer configMapsByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, ReadType readType) {

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
	static MultipleSourcesContainer secretsByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels, Environment environment, ReadType readType) {

		List<StrippedSourceContainer> strippedSecrets;

		if (readType.equals(ReadType.BATCH)) {
			LOG.debug(() -> "Will read all secrets in namespace : " + namespace);
			strippedSecrets = strippedSecrets(client, namespace);
		}
		else {
			LOG.debug(() -> "Will read individual secrets in namespace : " + namespace + " with labels : " + labels);
			strippedSecrets = strippedSecrets(client, namespace, labels);
		}

		return processLabeledData(strippedSecrets, environment, labels, namespace, true);
	}

	static List<StrippedSourceContainer> stripConfigMaps(List<ConfigMap> configMaps) {
		return configMaps.stream()
			.map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
					configMap.getMetadata().getName(), configMap.getData()))
			.toList();
	}

	static List<StrippedSourceContainer> stripSecrets(List<Secret> secrets) {
		return secrets.stream()
			.map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(), secret.getMetadata().getName(),
					secret.getData()))
			.toList();
	}

}
