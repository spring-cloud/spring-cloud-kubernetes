/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
public class ConfigMapConfigPropertiesTests {

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where prefix is empty
	 */
	@Test
	public void testUseNameAsPrefixUnsetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(sources.get(0).getPrefix(), "",
				"empty sources must generate a List with a single NormalizedSource, where prefix is empty");
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          useNameAsPrefix: true
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where prefix is empty, even if
	 * "useNameAsPrefix: true", because sources are empty
	 */
	@Test
	public void testUseNameAsPrefixSetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setUseNameAsPrefix(true);
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(sources.get(0).getPrefix(), "",
				"empty sources must generate a List with a single NormalizedSource, where prefix is empty,"
						+ "no matter of 'spring.cloud.kubernetes.config.useNameAsPrefix' value");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      config:
	 *        useNameAsPrefix: true
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: config-map-one
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where prefix will be equal to
	 * the config map name
	 */
	@Test
	public void testUseNameAsPrefixUnsetNonEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("config-map-one");
		properties.setSources(Collections.singletonList(one));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "a single NormalizedSource is expected");

		Assertions.assertEquals(sources.get(0).getPrefix(), "config-map-one");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      config:
	 *        useNameAsPrefix: true
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: config-map-one
	 *            useNameAsPrefix: false
	 *          - name: config-map-two
	 *            useNameAsPrefix: true
	 *          - name: config-map-three
	 * </pre>
	 *
	 * this test proves that 'spring.cloud.kubernetes.config.sources[].useNameAsPrefix'
	 * will override 'spring.cloud.kubernetes.config.useNameAsPrefix'. For the last entry
	 * in sources, since there is no explicit 'useNameAsPrefix', the one from
	 * 'spring.cloud.kubernetes.config.useNameAsPrefix' will be taken.
	 */
	@Test
	public void testUseNameAsPrefixSetNonEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("config-map-one");
		one.setUseNameAsPrefix(false);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setName("config-map-two");
		two.setUseNameAsPrefix(true);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setName("config-map-three");

		properties.setSources(Arrays.asList(one, two, three));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 3, "3 NormalizedSources are expected");

		Assertions.assertEquals(sources.get(0).getPrefix(), "");
		Assertions.assertEquals(sources.get(1).getPrefix(), "config-map-two");
		Assertions.assertEquals(sources.get(2).getPrefix(), "config-map-three");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      config:
	 *        useNameAsPrefix: false
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: config-map-one
	 *            useNameAsPrefix: false
	 *            explicitPrefix: one
	 *          - name: config-map-two
	 *            useNameAsPrefix: true
	 *            explicitPrefix: two
	 *          - name: config-map-three
	 *            explicitPrefix: three
	 *          - name: config-map-four
	 * </pre>
	 *
	 */
	@Test
	public void testMultipleCases() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(false);
		properties.setNamespace("spring-k8s");

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setNamespace("config-map-one");
		one.setUseNameAsPrefix(false);
		one.setExplicitPrefix("one");

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setNamespace("config-map-two");
		two.setUseNameAsPrefix(true);
		two.setExplicitPrefix("two");

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setNamespace("config-map-three");
		three.setExplicitPrefix("three");

		ConfigMapConfigProperties.Source four = new ConfigMapConfigProperties.Source();
		four.setNamespace("config-map-four");

		properties.setSources(Arrays.asList(one, two, three, four));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 4, "4 NormalizedSources are expected");

		Assertions.assertEquals(sources.get(0).getPrefix(), "one");
		Assertions.assertEquals(sources.get(1).getPrefix(), "two");
		Assertions.assertEquals(sources.get(2).getPrefix(), "three");
		Assertions.assertEquals(sources.get(3).getPrefix(), "");
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where useProfileNameAsSuffix
	 * will be true (this test proves that the change we added is not a breaking change
	 * for the already existing functionality)
	 */
	@Test
	public void testUseProfileNameAsSuffixNoChanges() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertTrue(sources.get(0).isUseProfileNameAsSuffix());
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          useProfileNameAsSuffix: false
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where useProfileNameAsSuffix
	 * will be false. Even if we did not define any sources explicitly, one will still be
	 * created, by default. That one might "flatMap" into multiple other, because of
	 * multiple profiles. As such this setting still matters and must be propagated to the
	 * normalized source.
	 */
	@Test
	public void testUseProfileNameAsSuffixDefaultChanged() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");
		properties.setUseProfileNameAsSuffix(false);

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertFalse(sources.get(0).isUseProfileNameAsSuffix());
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          useProfileNameAsSuffix: false
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 *        sources:
	 *          - name: one
	 *            useProfileNameAsSuffix: true
	 *          - name: two
	 *          - name: three
	 *            useProfileNameAsSuffix: false
	 * </pre>
	 *
	 * <pre>
	 * 	source "one" will have "useProfileNameAsSuffix = true".
	 * 	source "two" will have "useProfileNameAsSuffix = false".
	 * 	source "three" will have "useProfileNameAsSuffix = false".
	 * </pre>
	 */
	@Test
	public void testUseProfileNameAsSuffixDefaultChangedSourceOverride() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");
		properties.setUseProfileNameAsSuffix(false);

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("config-map-one");
		one.setUseProfileNameAsSuffix(true);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setName("config-map-two");

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setName("config-map-three");
		three.setUseProfileNameAsSuffix(false);

		properties.setSources(Arrays.asList(one, two, three));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 3);

		Assertions.assertTrue(sources.get(0).isUseProfileNameAsSuffix());
		Assertions.assertFalse(sources.get(1).isUseProfileNameAsSuffix());
		Assertions.assertFalse(sources.get(2).isUseProfileNameAsSuffix());
	}

}
