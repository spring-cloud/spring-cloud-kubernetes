/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Class that is supposed to test only ConfigUtils::rawDataContainsProfileBasedSource
 *
 * @author wind57
 */
class ConfigUtilsRawDataContainsProfileBasedSourceTests {

	@Test
	void nullSourceRawData() {
		List<String> activeProfiles = List.of();
		Map<String, String> rawData = null;

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertFalse(result);
	}

	@Test
	void sourceRawDataPresentEmptyActiveProfiles() {
		List<String> activeProfiles = List.of();
		Map<String, String> rawData = Map.of("account-k8s.yaml", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertFalse(result);
	}

	@Test
	void plainValuesOnly() {
		List<String> activeProfiles = List.of("k8s");
		Map<String, String> rawData = Map.of("account", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertFalse(result);
	}

	@Test
	void noMatchInActiveProfiles() {
		List<String> activeProfiles = List.of("k8s");
		Map<String, String> rawData = Map.of("account-dev.yml", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertFalse(result);
	}

	@Test
	void matchInActiveProfilesWithYml() {
		List<String> activeProfiles = List.of("dev");
		Map<String, String> rawData = Map.of("account-dev.yml", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertTrue(result);
	}

	@Test
	void matchInActiveProfilesWithYaml() {
		List<String> activeProfiles = List.of("dev", "k8s");
		Map<String, String> rawData = Map.of("account-dev.yaml", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertTrue(result);
	}

	@Test
	void matchInActiveProfilesWithProperties() {
		List<String> activeProfiles = List.of("dev", "k8s");
		Map<String, String> rawData = Map.of("account-dev.properties", "value");

		boolean result = ConfigUtils.rawDataContainsProfileBasedSource(activeProfiles, rawData).getAsBoolean();
		Assertions.assertTrue(result);
	}

}
