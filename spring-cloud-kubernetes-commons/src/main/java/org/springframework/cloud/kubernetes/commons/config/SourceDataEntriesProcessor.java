/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.KEY_VALUE_TO_PROPERTIES;
import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.PROPERTIES_TO_MAP;
import static org.springframework.cloud.kubernetes.commons.config.PropertySourceUtils.yamlParserGenerator;

/**
 * Processor that extracts data from an input, where input can be a single yaml/properties
 * file.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 * @author Michael Moudatsos
 */
public class SourceDataEntriesProcessor extends MapPropertySource {

	private static final Log LOG = LogFactory.getLog(SourceDataEntriesProcessor.class);

	public SourceDataEntriesProcessor(SourceData sourceData) {
		super(sourceData.sourceName(), sourceData.sourceData());
	}

	public static Map<String, Object> processAllEntries(Map<String, String> input, Environment environment) {

		Set<Map.Entry<String, String>> entrySet = input.entrySet();
		if (entrySet.size() == 1) {
			// we handle the case where the configmap contains a single "file"
			// in this case we don't care what the name of the file is
			Map.Entry<String, String> singleEntry = entrySet.iterator().next();
			String propertyName = singleEntry.getKey();
			String propertyValue = singleEntry.getValue();
			if (propertyName.endsWith(".yml") || propertyName.endsWith(".yaml")) {
				LOG.debug("The single property with name: [" + propertyName + "] will be treated as a yaml file");
				return yamlParserGenerator(environment).andThen(PROPERTIES_TO_MAP).apply(propertyValue);
			}
			else if (propertyName.endsWith(".properties")) {
				LOG.debug("The single property with name: [" + propertyName + "] will be treated as a properties file");
				return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(propertyValue);
			}
		}

		return defaultProcessAllEntries(input, environment);
	}

	/**
	 * <pre>
	 * 		we want to sort entries coming from the k8s source in a specific way:
	 *
	 * 	    1. "application.yaml/yml/properties" have to come first
	 * 	       (or the value from spring.application.name)
	 * 	    2. then profile specific entries, like "application-dev.yaml"
	 * 	    3. then plain properties
	 * </pre>
	 */
	static List<Map.Entry<String, String>> sorted(Map<String, String> input, Environment environment) {

		record WeightedEntry(Map.Entry<String, String> entry, int weight) {

		}

		// we pass empty Strings on purpose, the logic here is either the value of
		// "spring.application.name" or literal "application".
		String applicationName = ConfigUtils.getApplicationName(environment, "", "");
		String[] activeProfiles = environment.getActiveProfiles();

		// the order here is important, first has to come "application.yaml" and then
		// "application-dev.yaml"
		List<String> orderedFileNames = Stream.concat(Stream.of(applicationName),
				Arrays.stream(activeProfiles).map(profile -> applicationName + "-" + profile)).toList();

		int current = orderedFileNames.size() - 1;
		List<WeightedEntry> weightedEntries = new ArrayList<>();
		for (Map.Entry<String, String> entry : input.entrySet()) {
			String key = entry.getKey();
			if (key.endsWith(".yml") || key.endsWith(".yaml") || key.endsWith(".properties")) {
				String withoutExtension = key.split("\\.", 2)[0];
				int index = orderedFileNames.indexOf(withoutExtension);
				if (index >= 0) {
					weightedEntries.add(new WeightedEntry(entry, index));
				}
				else {
					LOG.warn("entry : " + key + " will be skipped");
				}
			}
			else {
				weightedEntries.add(new WeightedEntry(entry, ++current));
			}
		}

		return weightedEntries.stream().sorted(Comparator.comparing(WeightedEntry::weight)).map(WeightedEntry::entry)
				.toList();
	}

	private static Map<String, Object> defaultProcessAllEntries(Map<String, String> input, Environment environment) {

		List<Map.Entry<String, String>> sortedEntries = sorted(input, environment);
		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<String, String> entry : sortedEntries) {
			result.putAll(extractProperties(entry.getKey(), entry.getValue(), environment));
		}
		return result;

	}

	private static Map<String, Object> extractProperties(String resourceName, String content, Environment environment) {

		if (resourceName.endsWith(".yml") || resourceName.endsWith(".yaml") || resourceName.endsWith(".properties")) {

			if (resourceName.endsWith(".properties")) {
				LOG.debug("entry : " + resourceName + " will be treated as a single properties file");
				return KEY_VALUE_TO_PROPERTIES.andThen(PROPERTIES_TO_MAP).apply(content);
			}
			else {
				LOG.debug("entry : " + resourceName + " will be treated as a single yml/yaml file");
				return yamlParserGenerator(environment).andThen(PROPERTIES_TO_MAP).apply(content);
			}
		}

		return Collections.singletonMap(resourceName, content);

	}

}
