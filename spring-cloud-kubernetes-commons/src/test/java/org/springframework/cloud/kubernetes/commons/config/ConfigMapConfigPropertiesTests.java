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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Assertions;
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
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true,
				"config-map-a", "spring-k8s", false, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(1);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).prefix()).isSameAs(ConfigUtils.Prefix.DEFAULT);
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
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true,
				"config-map-a", "spring-k8s", true, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(1);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).prefix()).isSameAs(ConfigUtils.Prefix.DEFAULT);
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

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source("config-map-one", null,
				Collections.emptyMap(), null, null, null);

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(one), Map.of(),
				true, "config-map-a", "spring-k8s", true, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(1);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).prefix().prefixProvider().get())
			.isEqualTo("config-map-one");
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

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source("config-map-one", null,
				Collections.emptyMap(), null, false, null);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source("config-map-two", null,
				Collections.emptyMap(), null, true, null);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source("config-map-three", null,
				Collections.emptyMap(), null, true, null);

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(one, two, three),
				Map.of(), true, "config-map-a", "spring-k8s", true, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(3);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).prefix()).isSameAs(ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(1)).prefix().prefixProvider().get())
			.isEqualTo("config-map-two");
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(2)).prefix().prefixProvider().get())
			.isEqualTo("config-map-three");
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

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source("config-map-one", null,
				Collections.emptyMap(), "one", false, null);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source("config-map-two", null,
				Collections.emptyMap(), "two", true, null);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source("config-map-three", null,
				Collections.emptyMap(), "three", false, null);

		ConfigMapConfigProperties.Source four = new ConfigMapConfigProperties.Source(null, "config-map-four",
				Collections.emptyMap(), null, false, null);

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(),
				List.of(one, two, three, four), Map.of(), true, "config-map-a", "spring-k8s", true, false, false,
				RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(4);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).prefix().prefixProvider().get())
			.isEqualTo("one");
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(1)).prefix().prefixProvider().get())
			.isEqualTo("two");
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(2)).prefix().prefixProvider().get())
			.isEqualTo("three");
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(3)).prefix()).isSameAs(ConfigUtils.Prefix.DEFAULT);
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

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true,
				"config-map-a", "spring-k8s", false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(1);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources()).isTrue();
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

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(), Map.of(), true,
				"config-map-a", "spring-k8s", false, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(1);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources()).isTrue();
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

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source("config-map-one", null,
				Collections.emptyMap(), "one", null, true);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source("config-map-two", null,
				Collections.emptyMap(), null, false, null);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source("config-map-three", null,
				Collections.emptyMap(), null, null, false);

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(), List.of(one, two, three),
				Map.of(), true, "config-map-a", "spring-k8s", false, false, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertThat(sources.size()).isEqualTo(3);

		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(0)).profileSpecificSources()).isTrue();
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(1)).profileSpecificSources()).isFalse();
		Assertions.assertThat(((NamedConfigMapNormalizedSource) sources.get(2)).profileSpecificSources()).isFalse();
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

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source(null, null,
				Map.of("first-label", "configmap-one"), "one", false, null);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source(null, null,
				Map.of("second-label", "configmap-two"), "two", true, true);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source(null, null,
				Map.of("third-label", "configmap-three"), "three", null, null);

		ConfigMapConfigProperties.Source four = new ConfigMapConfigProperties.Source(null, null,
				Map.of("fourth-label", "configmap-four"), null, null, null);

		ConfigMapConfigProperties properties = new ConfigMapConfigProperties(true, List.of(),
				List.of(one, two, three, four), Map.of(), true, "config-map-a", "spring-k8s", false, false, false,
				RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		// we get 8 property sources, since "named" ones with "application" are
		// duplicated.
		// that's OK, since later in the code we get a LinkedHashSet out of them all,
		// so they become 5 only.
		Assertions.assertThat(sources.size()).isEqualTo(8);

		LabeledConfigMapNormalizedSource labeled1 = (LabeledConfigMapNormalizedSource) sources.get(1);
		Assertions.assertThat(labeled1.prefix().prefixProvider().get()).isEqualTo("one");
		Assertions.assertThat(labeled1.profileSpecificSources()).isTrue();

		LabeledConfigMapNormalizedSource labeled3 = (LabeledConfigMapNormalizedSource) sources.get(3);
		Assertions.assertThat(labeled3.prefix().prefixProvider().get()).isEqualTo("two");
		Assertions.assertThat(labeled3.profileSpecificSources()).isTrue();

		LabeledConfigMapNormalizedSource labeled5 = (LabeledConfigMapNormalizedSource) sources.get(5);
		Assertions.assertThat(labeled5.prefix().prefixProvider().get()).isEqualTo("three");
		Assertions.assertThat(labeled5.profileSpecificSources()).isFalse();

		LabeledConfigMapNormalizedSource labeled7 = (LabeledConfigMapNormalizedSource) sources.get(7);
		Assertions.assertThat(labeled7.prefix()).isSameAs(ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThat(labeled7.profileSpecificSources()).isFalse();

		Set<NormalizedSource> set = new LinkedHashSet<>(sources);
		Assertions.assertThat(set.size()).isEqualTo(5);
	}

}
