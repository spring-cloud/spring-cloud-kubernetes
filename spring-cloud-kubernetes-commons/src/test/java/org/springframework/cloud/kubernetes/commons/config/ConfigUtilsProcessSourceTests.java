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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Class that is supposed to test only ConfigUtils::processSource
 * and ConfigUtils::processNamedData.
 *
 * @author wind57
 */
class ConfigUtilsProcessSourceTests {

	/*
	 * <pre>
	 *		- includeDefaultProfileData         = true
	 * 		- emptyActiveProfiles               = does not matter
	 *  	- profileBasedSourceName            = does not matter
	 *      - defaultProfilePresent             = does not matter
	 *      - rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'includeDefaultProfileData=true', all other arguments are irrelevant
	 * and method must return 'true'.
 	 */
	@Test
	void testProcessSourceOne() {
		boolean includeDefaultProfileData = true;
		Environment environment = new MockEnvironment();
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of();

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/*
	 * this case is not very "interesting" because 'includeDefaultProfileData = true'
	 * which denotes a request not from config server; and such cases are tested in various
	 * other tests, before we fixed:
	 * https://github.com/spring-cloud/spring-cloud-kubernetes/pull/1600
	 */
	@Test
	void testProcessNamedDataOne() {
		List<StrippedSourceContainer> strippedSources = List.of(
			new StrippedSourceContainer(Map.of(), "configmap-a", Map.of("one", "1"))
		);
		Environment environment = new MockEnvironment();
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of("configmap-a"));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = true;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment,
			sourceNames, namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[configmap-a]");
		Assertions.assertEquals(result.data(), Map.of("one", "1"));
	}

}
