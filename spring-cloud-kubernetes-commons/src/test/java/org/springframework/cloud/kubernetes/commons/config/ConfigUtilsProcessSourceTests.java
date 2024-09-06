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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Class that is supposed to test only ConfigUtils::processSource and
 * ConfigUtils::processNamedData.
 *
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class ConfigUtilsProcessSourceTests {

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = true
	 *		- emptyActiveProfiles               = does not matter
	 *		- profileBasedSourceName            = does not matter
	 * 		- defaultProfilePresent             = does not matter
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'includeDefaultProfileData=true', all other arguments are irrelevant and
	 * method must return 'true'.
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

	/**
	 * this case is not very "interesting" because 'includeDefaultProfileData = true'
	 * which denotes a request not from config server; and such cases are tested in
	 * various other tests, before we fixed:
	 * https://github.com/spring-cloud/spring-cloud-kubernetes/pull/1600
	 */
	@Test
	void testProcessNamedDataOne() {
		List<StrippedSourceContainer> strippedSources = List
			.of(new StrippedSourceContainer(Map.of(), "configmap-a", Map.of("one", "1")));
		Environment environment = new MockEnvironment();
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of("configmap-a"));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = true;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, sourceNames,
				namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[configmap-a]");
		Assertions.assertEquals(result.data(), Map.of("one", "1"));
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = false
	 * 		- defaultProfilePresent             = true
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'defaultProfilePresent=true', this method must return 'true'.
	 */
	@Test
	void testProcessSourceTwo() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("default");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of();

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- request is coming from config server
	 *		- activeProfile = ['default']
	 *		- sourceName = 'account'
	 * </pre>
	 *
	 * As such the above will generate:
	 *
	 * <pre>
	 *     - includeDefaultProfileData         = false
	 * 	   - emptyActiveProfiles               = false
	 * 	   - profileBasedSourceName            = false
	 * 	   - defaultProfilePresent             = true
	 * 	   - rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * In this case, three types of properties will be read from the source:
	 *
	 * <pre>
	 *     - all simple properties
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}.{EXTENSION}"
	 *       (in our case 'account.properties')
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}-${ACTIVE_PROFILE}.{EXTENSION}"
	 *       (in our case 'account-default.properties')
	 *     - there are strict sorting rules if both of the above are matched
	 * </pre>
	 */
	@Test
	void testProcessNamedDataTwo(CapturedOutput output) {
		// @formatter:off
		Map<String, String> sourceRawData = Map.of(
			"one", "1",
			"two", "2",
			"account-default.properties", "five=5",
			"account.properties", "one=11\nthree=3",
			"account-k8s.properties", "one=22\nfour=4"
		);
		// @formatter:on
		String sourceName = "account";
		List<StrippedSourceContainer> strippedSources = List
			.of(new StrippedSourceContainer(Map.of(), sourceName, sourceRawData));
		MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", sourceName);
		environment.setActiveProfiles("default");
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of(sourceName));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = false;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, sourceNames,
				namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[account]");

		/**
		 * <pre>
		 * there are some things to see here:
		 *
		 * 		1. 'three=3' is present in the result, which means we have read 'account.properties'
		 *		2. 'five=5' is present in the result, which means we have read 'account-default.properties'
		 *		3. even if we have read 'account.properties', we have 'one=1' (and not 'one=11'),
		 *			since simple properties override the ones from yml/yaml/properties
		 *		4. 'four=4' is not present in the result, because we do not read 'account-k8s.properties',
		 *			since 'k8s' is not an active profile.
		 * </pre>
		 */
		Assertions.assertEquals(result.data(), Map.of("one", "1", "two", "2", "three", "3", "five", "5"));
		Assertions.assertTrue(output.getOut().contains("entry : account-k8s.properties will be skipped"));
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 * 		- profileBasedSourceName            = true
	 * 		- defaultProfilePresent             = false
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * Since 'profileBasedSourceName=true', this method must return 'true'.
	 */
	@Test
	void testProcessSourceThree() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("default");
		String sourceName = "account-default";
		Map<String, String> sourceRawData = Map.of();

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- request is coming from config server
	 *		- activeProfile = ['default']
	 *		- sourceName = 'account-default'
	 * </pre>
	 *
	 * As such the above will generate:
	 *
	 * <pre>
	 *     - includeDefaultProfileData         = false
	 * 	   - emptyActiveProfiles               = false
	 * 	   - profileBasedSourceName            = true
	 * 	   - defaultProfilePresent             = does not matter
	 * 	   - rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * In this case, three types of properties will be read from the source:
	 *
	 * <pre>
	 *     - all simple properties
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}.{EXTENSION}"
	 *       (in our case 'account.properties')
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}-${ACTIVE_PROFILE}.{EXTENSION}"
	 *       (in our case 'account-default.properties')
	 *     - there are strict sorting rules if both of the above are matched
	 * </pre>
	 */
	@Test
	void testProcessNamedDataThree(CapturedOutput output) {
		// @formatter:off
		Map<String, String> sourceRawData = Map.of(
			"one", "1",
			"two", "2",
			"account.properties", "one=11\nthree=3",
			"account-default.properties", "one=111\nfive=5",
			"account-k8s.properties", "one=22\nfour=4"
		);
		// @formatter:on
		String sourceName = "account-default";
		List<StrippedSourceContainer> strippedSources = List
			.of(new StrippedSourceContainer(Map.of(), sourceName, sourceRawData));
		MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "account");
		environment.setActiveProfiles("default");
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of(sourceName));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = false;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, sourceNames,
				namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[account-default]");

		/**
		 * <pre>
		 * there are some things to see here:
		 *
		 *		1. 'one=1' is present in the result, which means we have read simple properties.
		 *		2. 'two-2' is present in the result, which means we have read simple properties.
		 *		3. even if we have read 'account.properties', we have 'one=1' (and not 'one=11'),
		 *			since simple properties override the ones from yml/yaml/properties
		 *		4. 'three=3' is present in the result, which means we have read 'account.properties'.
		 *		5. 'five=5' is present in the result, which means we have read 'account-default.properties'
		 *			(but we don't have 'one=111', instead : 'one=1')
		 *		6. we do not have 'four=4' since we do not read 'account-k8s.properties'
		 * </pre>
		 */
		Assertions.assertEquals(result.data(), Map.of("one", "1", "two", "2", "three", "3", "five", "5"));
		Assertions.assertTrue(output.getOut().contains("entry : account-k8s.properties will be skipped"));

	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = false
	 *		- defaultProfilePresent             = false
	 *		- rawDataContainsProfileBasedSource = false
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceFour() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of("one", "1");

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertFalse(result);
	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 * 		- emptyActiveProfiles               = false
	 * 		- profileBasedSourceName            = false
	 * 		- defaultProfilePresent             = false
	 * 		- rawDataContainsProfileBasedSource = true
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceFive() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account";
		Map<String, String> sourceRawData = Map.of("one", "1", "account-k8s.properties", "one=11");

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- request is coming from config server
	 *		- activeProfile = ['k8s']
	 *		- sourceName = 'account'
	 *	    - rawData inside the source has an entry : 'account-k8s.properties'
	 * </pre>
	 *
	 * As such the above will generate:
	 *
	 * <pre>
	 *     - includeDefaultProfileData         = false
	 * 	   - emptyActiveProfiles               = false
	 * 	   - profileBasedSourceName            = false
	 * 	   - defaultProfilePresent             = false
	 * 	   - rawDataContainsProfileBasedSource = true
	 * </pre>
	 *
	 * In this case, only one type of source data will be read
	 *
	 * <pre>
	 *     - "${SOURCE_NAME}-${ACTIVE_PROFILE}.{EXTENSION}"
	 * </pre>
	 */
	@Test
	void testProcessNamedDataFive(CapturedOutput output) {
		// @formatter:off
		Map<String, String> sourceRawData = Map.of(
			"one", "1",
			"account.properties", "one=11\ntwo=2",
			"account-default.properties", "one=111\nthree=3",
			"account-k8s.properties", "one=1111\nfour=4"
		);
		// @formatter:on
		String sourceName = "account";
		List<StrippedSourceContainer> strippedSources = List
			.of(new StrippedSourceContainer(Map.of(), sourceName, sourceRawData));
		MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", sourceName);
		environment.setActiveProfiles("k8s");
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of(sourceName));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = false;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, sourceNames,
				namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[account]");

		/**
		 * <pre>
		 * 		- we only read from 'account-k8s.properties'
		 * </pre>
		 */
		Assertions.assertEquals(result.data(), Map.of("one", "1111", "four", "4"));
		Assertions.assertTrue(output.getOut().contains("entry : account.properties will be skipped"));
		Assertions.assertTrue(output.getOut().contains("entry : account-default.properties will be skipped"));

	}

	/**
	 * <pre>
	 *		- includeDefaultProfileData         = false
	 *		- emptyActiveProfiles               = false
	 *		- profileBasedSourceName            = true
	 * 		- defaultProfilePresent             = does not matter
	 * 		- rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 */
	@Test
	void testProcessSourceSix() {
		boolean includeDefaultProfileData = false;
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("k8s");
		String sourceName = "account-k8s";
		Map<String, String> sourceRawData = Map.of("one", "1", "account-k8s.properties", "one=11");

		boolean result = ConfigUtils.processSource(includeDefaultProfileData, environment, sourceName, sourceRawData);
		Assertions.assertTrue(result);
	}

	/**
	 * <pre>
	 *		- request is coming from config server
	 *		- activeProfile = ['k8s']
	 *		- sourceName = 'account-k8s'
	 * </pre>
	 *
	 * As such the above will generate:
	 *
	 * <pre>
	 *     - includeDefaultProfileData         = false
	 * 	   - emptyActiveProfiles               = false
	 * 	   - profileBasedSourceName            = true
	 * 	   - defaultProfilePresent             = does not matter
	 * 	   - rawDataContainsProfileBasedSource = does not matter
	 * </pre>
	 *
	 * In this case, a few types of data will be read:
	 *
	 * <pre>
	 *     - all simple properties
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}.{EXTENSION}"
	 *       (in our case 'account.properties')
	 *     - all nested ones (yaml/yml/properties themselves) that match "${SOURCE_NAME}-${ACTIVE_PROFILE}.{EXTENSION}"
	 *       (in our case 'account-k8s.properties')
	 *     - there are strict sorting rules if both of the above are matched
	 * </pre>
	 */
	@Test
	void testProcessNamedDataSix(CapturedOutput output) {
		// @formatter:off
		Map<String, String> sourceRawData = Map.of(
			"one", "1",
			"two", "2",
			"account.properties", "one=11\nthree=3",
			"account-default.properties", "one=111\nfour=4",
			"account-k8s.properties", "one=1111\nfive=5",
			"account-prod.properties", "six=6"
		);
		// @formatter:on
		String sourceName = "account-k8s";
		List<StrippedSourceContainer> strippedSources = List
			.of(new StrippedSourceContainer(Map.of(), sourceName, sourceRawData));
		MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "account");
		environment.setActiveProfiles("k8s");
		LinkedHashSet<String> sourceNames = new LinkedHashSet<>(List.of(sourceName));
		String namespace = "namespace-a";
		boolean decode = false;
		boolean includeDefaultProfileData = false;

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, sourceNames,
				namespace, decode, includeDefaultProfileData);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.names().toString(), "[account-k8s]");

		/**
		 * <pre>
		 * there are some things to see here:
		 *
		 * 		1. 'one=1' is not present in the result, we are not supposed to read simple properties.
		 *		2. 'two-2' is not present in the result, we are not supposed to read simple properties.
		 *		3. even if we have read 'account.properties', we have 'one=1' (and not 'one=11'),
		 *			since simple properties override the ones from yml/yaml/properties
		 *		4. 'three=3' is not present in the result, we are not supposed to read '${SPRING>APPLICATION.NAME}'
		 *			properties.
		 *		5. 'four=4' is not present in the result, which means we have not read 'account-default.properties'
		 *			(but we don't have 'one=111', instead : 'one=1')
		 *		6. we do not have 'three=3' since we do not read 'account-default.properties'
		 *		7. we do not have 'six=6' since we do not read 'account-prod.properties'
		 *			(because 'prod' is not an active profile)
		 * </pre>
		 */
		Assertions.assertEquals(result.data(), Map.of("one", "1111", "five", "5"));
		Assertions.assertTrue(output.getOut().contains("entry : account-prod.properties will be skipped"));
		Assertions.assertTrue(output.getOut().contains("entry : account.properties will be skipped"));
		Assertions.assertTrue(output.getOut().contains("entry : account-default.properties will be skipped"));

	}

}
