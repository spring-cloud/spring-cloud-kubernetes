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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class SecretsConfigPropertiesTests {

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        secrets:
	 *          name: secret-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a configuration as above will result in a NormalizedSource where prefix is empty
	 */
	@Test
	void testUseNameAsPrefixUnsetEmptySources() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("secret-a");
		properties.setNamespace("spring-k8s");

		List<SecretsConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(sources.get(0).getPrefix(), "",
				"empty sources must generate a List with a single NormalizedSource, where prefix is empty");
	}

	/**
	 * <pre>
	 * 	spring:
	 *	  cloud:
	 *      kubernetes:
	 *        secrets:
	 *          useNameAsPrefix: true
	 *          name: secret-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a configuration as above will result in a NormalizedSource where prefix is empty,
	 * even if "useNameAsPrefix: true", because sources are empty
	 */
	@Test
	void testUseNameAsPrefixSetEmptySources() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setUseNameAsPrefix(true);
		properties.setName("secret-a");
		properties.setNamespace("spring-k8s");

		List<SecretsConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(sources.get(0).getPrefix(), "",
				"empty sources must generate a List with a single NormalizedSource, where prefix is empty,"
						+ "no matter of 'spring.cloud.kubernetes.secrets.useNameAsPrefix' value");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      secrets:
	 *        useNameAsPrefix: true
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: secret-one
	 * </pre>
	 *
	 * a configuration as above will result in a NormalizedSource where prefix will be
	 * equal to the secret name
	 */
	@Test
	void testUseNameAsPrefixUnsetNonEmptySources() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setName("secret-one");
		properties.setSources(Collections.singletonList(one));

		List<SecretsConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "a single NormalizedSource is expected");

		Assertions.assertEquals(sources.get(0).getPrefix(), "secret-one");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      secrets:
	 *        useNameAsPrefix: true
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: secret-one
	 *            useNameAsPrefix: false
	 *          - name: secret-two
	 *            useNameAsPrefix: true
	 *          - name: secret-three
	 * </pre>
	 *
	 * this test proves that 'spring.cloud.kubernetes.secrets.sources[].useNameAsPrefix'
	 * will override 'spring.cloud.kubernetes.secrets.useNameAsPrefix'. For the last entry
	 * in sources, since there is no explicit 'useNameAsPrefix', the one from
	 * 'spring.cloud.kubernetes.secrets.useNameAsPrefix' will be taken.
	 */
	@Test
	void testUseNameAsPrefixSetNonEmptySources() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setName("secret-one");
		one.setUseNameAsPrefix(false);

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source();
		two.setName("secret-two");
		two.setUseNameAsPrefix(true);

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source();
		three.setName("secret-three");

		properties.setSources(Arrays.asList(one, two, three));

		List<SecretsConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 3, "3 NormalizedSources are expected");

		Assertions.assertEquals(sources.get(0).getPrefix(), "");
		Assertions.assertEquals(sources.get(1).getPrefix(), "secret-two");
		Assertions.assertEquals(sources.get(2).getPrefix(), "secret-three");
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      secrets:
	 *        useNameAsPrefix: false
	 *        namespace: spring-k8s
	 *        sources:
	 *          - name: secrets-one
	 *            useNameAsPrefix: false
	 *            explicitPrefix: one
	 *          - name: secrets-two
	 *            useNameAsPrefix: true
	 *            explicitPrefix: two
	 *          - name: secrets-three
	 *            explicitPrefix: three
	 *          - name: secrets-four
	 * </pre>
	 *
	 */
	@Test
	void testMultipleCases() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setUseNameAsPrefix(false);
		properties.setNamespace("spring-k8s");

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setNamespace("config-map-one");
		one.setUseNameAsPrefix(false);
		one.setExplicitPrefix("one");

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source();
		two.setNamespace("secrets-two");
		two.setUseNameAsPrefix(true);
		two.setExplicitPrefix("two");

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source();
		three.setNamespace("secrets-three");
		three.setExplicitPrefix("three");

		SecretsConfigProperties.Source four = new SecretsConfigProperties.Source();
		four.setNamespace("secrets-four");

		properties.setSources(Arrays.asList(one, two, three, four));

		List<SecretsConfigProperties.NormalizedSource> sources = properties.determineSources();
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
	 *        secrets:
	 *          name: secret-a
	 *        	namespace: spring-k8s
	 * </pre>
	 *
	 * a config as above will result in a NormalizedSource where
	 * includeProfileSpecificSources will be true (this test proves that the change we
	 * added is not a breaking change for the already existing functionality)
	 */
	// TODO enable back when I will add support for profileSpecificSources
	@Disabled
	@Test
	void testUseIncludeProfileSpecificSourcesNoChanges() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertTrue(sources.get(0).isIncludeProfileSpecificSources());
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
	// TODO enable back when I will add support for profileSpecificSources
	@Disabled
	@Test
	void testUseIncludeProfileSpecificSourcesDefaultChanged() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("config-map-a");
		properties.setNamespace("spring-k8s");
		properties.setIncludeProfileSpecificSources(false);

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertFalse(sources.get(0).isIncludeProfileSpecificSources());
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
	// TODO enable back when I will add support for profileSpecificSources
	@Disabled
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

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(sources.size(), 3);

		Assertions.assertTrue(sources.get(0).isIncludeProfileSpecificSources());
		Assertions.assertFalse(sources.get(1).isIncludeProfileSpecificSources());
		Assertions.assertFalse(sources.get(2).isIncludeProfileSpecificSources());
	}

	// a test that shows that hashCode and equality is based on name, namespace
	// and labels only
	@Test
	void testHashCodeAndEquality() {
		SecretsConfigProperties.NormalizedSource left = new SecretsConfigProperties.NormalizedSource("name",
				"namespace", Collections.singletonMap("a", "b"), "leftPrefix");

		SecretsConfigProperties.NormalizedSource right = new SecretsConfigProperties.NormalizedSource("name",
				"namespace", Collections.singletonMap("a", "b"), "rightPrefix");

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

}
