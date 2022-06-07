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

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class ConfigMapConfigPropertiesTests {

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
	void testUseNameAsPrefixUnsetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertSame(((NamedConfigMapNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT,
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
	void testUseNameAsPrefixSetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setUseNameAsPrefix(true);
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertSame(((NamedConfigMapNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT,
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
	void testUseNameAsPrefixUnsetNonEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("config-map-one");
		properties.setSources(Collections.singletonList(one));

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "a single NormalizedSource is expected");

		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(0)).prefix().prefixProvider().get(),
				"config-map-one");
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
	void testUseNameAsPrefixSetNonEmptySources() {
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

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 3, "3 NormalizedSources are expected");

		Assertions.assertSame(((NamedConfigMapNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT);
		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(1)).prefix().prefixProvider().get(),
				"config-map-two");
		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(2)).prefix().prefixProvider().get(),
				"config-map-three");
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
	void testMultipleCases() {
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

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 4, "4 NormalizedSources are expected");

		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(0)).prefix().prefixProvider().get(),
				"one");
		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(1)).prefix().prefixProvider().get(),
				"two");
		Assertions.assertEquals(((NamedConfigMapNormalizedSource) sources.get(2)).prefix().prefixProvider().get(),
				"three");
		Assertions.assertSame(((NamedConfigMapNormalizedSource) sources.get(3)).prefix(), ConfigUtils.Prefix.DEFAULT);
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
	 * a config as above will result in a NormalizedSource where
	 * includeProfileSpecificSources will be true (this test proves that the change we
	 * added is not a breaking change for the already existing functionality)
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesNoChanges() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertTrue(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources());
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          includeProfileSpecificSources: false
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where
	 * includeProfileSpecificSources will be false. Even if we did not define any sources
	 * explicitly, one will still be created, by default. That one might "flatMap" into
	 * multiple other, because of multiple profiles. As such this setting still matters
	 * and must be propagated to the normalized source.
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesDefaultChanged() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");
		properties.setIncludeProfileSpecificSources(false);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertFalse(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources());
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        config:
	 *          includeProfileSpecificSources: false
	 *          name: config-map-a
	 *        	namespace: spring-k8s
	 *        sources:
	 *          - name: one
	 *            includeProfileSpecificSources: true
	 *          - name: two
	 *          - name: three
	 *            includeProfileSpecificSources: false
	 * </pre>
	 *
	 * <pre>
	 * 	source "one" will have "includeProfileSpecificSources = true".
	 * 	source "two" will have "includeProfileSpecificSources = false".
	 * 	source "three" will have "includeProfileSpecificSources = false".
	 * </pre>
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesDefaultChangedSourceOverride() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");
		properties.setIncludeProfileSpecificSources(false);

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("config-map-one");
		one.setIncludeProfileSpecificSources(true);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setName("config-map-two");

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setName("config-map-three");
		three.setIncludeProfileSpecificSources(false);

		properties.setSources(Arrays.asList(one, two, three));

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 3);

		Assertions.assertTrue(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources());
		Assertions.assertFalse(((NamedConfigMapNormalizedSource) sources.get(1)).profileSpecificSources());
		Assertions.assertFalse(((NamedConfigMapNormalizedSource) sources.get(2)).profileSpecificSources());
	}

}
