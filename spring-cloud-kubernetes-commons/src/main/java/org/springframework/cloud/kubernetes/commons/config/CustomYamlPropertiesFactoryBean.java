/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.logging.LogFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.reader.UnicodeReader;

import org.springframework.core.CollectionFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import static org.springframework.beans.factory.config.YamlProcessor.DocumentMatcher;
import static org.springframework.beans.factory.config.YamlProcessor.MatchStatus;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_CONFIG_ACTIVATE_ON_PROFILE;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_PROFILES;

/**
 * A class based on
 * {@link org.springframework.beans.factory.config.YamlPropertiesFactoryBean} that takes
 * care to override profile-based collections and maps.
 *
 * Unlike YamlPropertiesFactoryBean, which flattens collections and maps, this one does things a bit different.
 *
 * <ul>
 *     <li>read all the documents in a yaml file</li>
 *     <li>flatten all properties besides collection and maps,
 *     		YamlPropertiesFactoryBean does not do that and starts flattening everything</li>
 *     <li>take only those that match the document matchers</li>
 *     <li>split them in two : those that have profile activation and those that don't</li>
 *     <li>override properties in the non-profile based yamls with the ones from profile based ones.
 *     This achieves the same result as a plain spring-boot app, where profile based properties have a higher
 *     precedence.</li>
 *     <li>once the overriding happened, we do another flattening, this time including collection and maps</li>
 * </ul>
 *
 * @author wind57
 */
final class CustomYamlPropertiesFactoryBean {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(CustomYamlPropertiesFactoryBean.class));

	private List<DocumentMatcher> documentMatchers = Collections.emptyList();

	Map<String, Object> createProperties(String source) {
		LinkedHashMap<String, Object> finalMap = new LinkedHashMap<>();
		Yaml yaml = new Yaml();

		Resource resource = new ByteArrayResource(source.getBytes(StandardCharsets.UTF_8));
		try (Reader reader = new UnicodeReader(resource.getInputStream())) {
			Iterable<Object> iterable = yaml.loadAll(reader);

			List<LinkedHashMap<String, Object>> allYamlDocuments = new ArrayList<>();

			// 1. read all the documents that are contained in the yaml (might be more
			// than one).
			// We flatten all properties besides collection and maps. This is needed to
			// be able to properly override them
			for (Object singleYamlDocument : iterable) {
				if (singleYamlDocument != null) {
					LinkedHashMap<String, Object> flattenedMap = new LinkedHashMap<>();
					Map<String, Object> originalSource = asMap(singleYamlDocument);
					buildFlattenedMapWithoutComplexObjects(flattenedMap, originalSource, null);
					allYamlDocuments.add(flattenedMap);
				}
			}

			// 2. take only those that match document matchers
			List<LinkedHashMap<String, Object>> yamlDocumentsMatchedAgainstDocumentMatchers = filterBasedOnDocumentMatchers(
					allYamlDocuments);

			// 3. split them in two: ones that do not have any profile activation
			// and ones that do have profile activation.
			Map<Boolean, List<LinkedHashMap<String, Object>>> partitioned = yamlDocumentsMatchedAgainstDocumentMatchers
				.stream()
				.collect(Collectors.partitioningBy(
						x -> !x.containsKey(SPRING_CONFIG_ACTIVATE_ON_PROFILE) && !x.containsKey(SPRING_PROFILES)));

			LOG.debug(() -> "non-profile source : " + partitioned.get(Boolean.TRUE).toString());
			LOG.debug(() -> "profile source : " + partitioned.get(Boolean.FALSE));

			// 4. once they are split, iterate and compute a single properties map
			// (with collections and maps unflattened yet), but correctly overridden.
			// Meaning non-profile-based sources come first.

			LinkedHashMap<String, Object> flattenedWithoutComplexObjects = new LinkedHashMap<>();
			partitioned.get(Boolean.TRUE).forEach(flattenedWithoutComplexObjects::putAll);
			partitioned.get(Boolean.FALSE).forEach(flattenedWithoutComplexObjects::putAll);

			// 5. we now know the correct order, let's do the final flattening
			buildFlattenedMap(finalMap, flattenedWithoutComplexObjects, null);

			LOG.debug(() -> "final source : " + finalMap);

		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

		return finalMap;
	}

	void setDocumentMatchers(DocumentMatcher... matchers) {
		this.documentMatchers = List.of(matchers);
	}

	private List<LinkedHashMap<String, Object>> filterBasedOnDocumentMatchers(
			List<LinkedHashMap<String, Object>> allDocuments) {
		return allDocuments.stream().filter(x -> {
			Properties properties = CollectionFactory.createStringAdaptingProperties();
			properties.putAll(x);
			MatchStatus matchStatus = MatchStatus.ABSTAIN;
			for (DocumentMatcher matcher : this.documentMatchers) {
				MatchStatus match = matcher.matches(properties);
				matchStatus = MatchStatus.getMostSpecific(match, matchStatus);
				if (match == MatchStatus.FOUND) {
					LOG.debug(() -> "Matched document with document matcher: " + properties);
					return true;
				}

				if (matchStatus == MatchStatus.ABSTAIN) {
					LOG.debug(() -> "Matched document with default matcher: " + properties);
					return true;
				}
			}
			return false;
		}).toList();
	}

	/**
	 * builds the flattened properties.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source,
			@Nullable String path) {
		source.forEach((key, value) -> {
			if (StringUtils.hasText(path)) {
				if (key.startsWith("[")) {
					key = path + key;
				}
				else {
					key = path + '.' + key;
				}
			}
			if (value instanceof String) {
				result.put(key, value);
			}
			else if (value instanceof Map map) {
				// Need a compound key
				buildFlattenedMap(result, map, key);
			}
			else if (value instanceof Collection collection) {
				// Need a compound key
				if (collection.isEmpty()) {
					result.put(key, "");
				}
				else {
					int count = 0;
					for (Object object : collection) {
						buildFlattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), key);
					}
				}
			}
			else {
				result.put(key, (value != null ? value : ""));
			}
		});
	}

	/**
	 * flatten properties, but without collections or maps. So it looks like this, for
	 * example: <pre>
	 *     bean.test=[{name=Alice, role=admin}, {name=ER, role=user}]}, {bean.items=[Item 10]}]
	 * </pre>
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void buildFlattenedMapWithoutComplexObjects(Map<String, Object> result, Map<String, Object> source,
			@Nullable String path) {
		source.forEach((key, value) -> {
			if (StringUtils.hasText(path)) {
				if (key.startsWith("[")) {
					key = path + key;
				}
				else {
					key = path + '.' + key;
				}
			}
			if (value instanceof String) {
				result.put(key, value);
			}
			else if (value instanceof Map map) {
				// Need a compound key
				buildFlattenedMapWithoutComplexObjects(result, map, key);
			}
			else if (value instanceof Collection collection) {
				if (collection.isEmpty()) {
					result.put(key, "");
				}
				else {
					result.put(key, collection);
				}
			}
			else {
				result.put(key, (value != null ? value : ""));
			}
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map<String, Object> asMap(Object object) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (object instanceof Map map) {
			map.forEach((key, value) -> {
				if (value instanceof Map) {
					value = asMap(value);
				}
				if (key instanceof CharSequence) {
					result.put(key.toString(), value);
				}
				else {
					// It has to be a map key in this case
					result.put("[" + key.toString() + "]", value);
				}
			});
		}

		return result;
	}

}
