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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	 * includeProfileSpecificSources will be true, thus active profiles are taken from
	 * environment (this test proves that the change we added is not a breaking change for
	 * the already existing functionality)
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesNoChanges() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a");
		List<NormalizedSource> sources = properties.determineSources(environment);
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(sources.get(0).profiles().iterator().next().name(), "a");
		Assertions.assertFalse(sources.get(0).profiles().iterator().next().strict());
		Assertions.assertFalse(sources.get(0).strict());
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

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a");
		List<NormalizedSource> sources = properties.determineSources(environment);
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals((sources.get(0)).profiles(), Set.of());
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

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("from-env");

		List<NormalizedSource> sources = properties.determineSources(environment);
		Assertions.assertEquals(sources.size(), 3);

		Assertions.assertEquals(sources.get(0).profiles().iterator().next().name(), "from-env");
		Assertions.assertFalse(sources.get(0).profiles().iterator().next().strict());
		Assertions.assertFalse(sources.get(0).strict());
		Assertions.assertEquals((sources.get(1)).profiles(), Set.of());
		Assertions.assertEquals((sources.get(2)).profiles(), Set.of());
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      config:
	 *        useNameAsPrefix: false
	 *        namespace: spring-k8s
	 *        includeProfileSpecificSources: false
	 *        sources:
	 *          - labels:
	 *              - name: first-label
	 *                value: configmap-one
	 *            useNameAsPrefix: false
	 *            explicitPrefix: one
	 *          - labels:
	 *          	- name: second-label
	 * 	          	  value: configmap-two
	 * 	          includeProfileSpecificSources: true
	 *            useNameAsPrefix: true
	 *            explicitPrefix: two
	 *          - labels:
	 *          	- name: third-label
	 * 	          	  value: configmap-three
	 *            explicitPrefix: three
	 *          - labels:
	 * 	         	- name: fourth-label
	 * 	           	  value: configmap-four
	 * </pre>
	 *
	 */
	@Test
	void testLabelsMultipleCases() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(false);
		properties.setNamespace("spring-k8s");
		properties.setIncludeProfileSpecificSources(false);

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setLabels(Map.of("first-label", "configmap-one"));
		one.setUseNameAsPrefix(false);
		one.setExplicitPrefix("one");

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setLabels(Map.of("second-label", "configmap-two"));
		two.setUseNameAsPrefix(true);
		two.setExplicitPrefix("two");
		two.setIncludeProfileSpecificSources(true);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setLabels(Map.of("third-label", "configmap-three"));
		three.setExplicitPrefix("three");

		ConfigMapConfigProperties.Source four = new ConfigMapConfigProperties.Source();
		four.setLabels(Map.of("fourth-label", "configmap-four"));

		properties.setSources(Arrays.asList(one, two, three, four));

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a");

		List<NormalizedSource> sources = properties.determineSources(environment);
		// we get 8 property sources, since "named" ones with "application" are
		// duplicated.
		// that's OK, since later in the code we get a LinkedHashSet out of them all,
		// so they become 5 only.
		Assertions.assertEquals(sources.size(), 8, "4 NormalizedSources are expected");

		LabeledConfigMapNormalizedSource labeled1 = (LabeledConfigMapNormalizedSource) sources.get(1);
		Assertions.assertEquals(labeled1.prefix().prefixProvider().get(), "one");
		Assertions.assertEquals(labeled1.profiles(), Set.of());

		LabeledConfigMapNormalizedSource labeled3 = (LabeledConfigMapNormalizedSource) sources.get(3);
		Assertions.assertEquals(labeled3.prefix().prefixProvider().get(), "two");
		Assertions.assertEquals(labeled3.profiles().iterator().next().name(), "a");
		Assertions.assertFalse(labeled3.profiles().iterator().next().strict());
		Assertions.assertFalse(labeled3.strict());

		LabeledConfigMapNormalizedSource labeled5 = (LabeledConfigMapNormalizedSource) sources.get(5);
		Assertions.assertEquals(labeled5.prefix().prefixProvider().get(), "three");
		Assertions.assertEquals(labeled5.profiles(), Set.of());

		LabeledConfigMapNormalizedSource labeled7 = (LabeledConfigMapNormalizedSource) sources.get(7);
		Assertions.assertSame(labeled7.prefix(), ConfigUtils.Prefix.DEFAULT);
		Assertions.assertEquals(labeled7.profiles(), Set.of());

		Set<NormalizedSource> set = new LinkedHashSet<>(sources);
		Assertions.assertEquals(5, set.size());
	}

}
