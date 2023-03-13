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
import java.util.Iterator;
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
class SecretsConfigPropertiesTests {

	/**
	 * the case when labels are empty
	 */
	@Test
	void emptySourcesSecretName() {

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(), List.of(), true,
				null, "namespace", false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> source = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(source.size(), 1);
		Assertions.assertTrue(source.get(0) instanceof NamedSecretNormalizedSource);
		Assertions.assertTrue(source.get(0).name().isPresent());
		Assertions.assertEquals(source.get(0).name().get(), "application");
	}

	/**
	 * <pre>
	 *     spring:
	 *        cloud:
	 *           kubernetes:
	 *             secrets:
	 *                sources:
	 *                   - name : one
	 *                     labels:
	 *                       one: "1"
	 *                   - labels:
	 *                       two: 2
	 *                   - labels:
	 *                       three: 3
	 * </pre>
	 *
	 * proves what there are 5 normalized sources after calling normalize method and put
	 * the result in a Set.
	 */
	@Test
	void multipleSources() {

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source("one", "spring-k8s", Map.of("one", "1"),
				null, false, false);

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source(null, "spring-k8s", Map.of("two", "2"),
				null, false, false);

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source(null, "spring-k8s",
				Map.of("three", "3"), null, false, false);

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(),
				List.of(one, two, three), true, null, "namespace", false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> result = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(result.size(), 6);

		Set<NormalizedSource> resultAsSet = new LinkedHashSet<>(result);
		Assertions.assertEquals(resultAsSet.size(), 5);

		Iterator<NormalizedSource> iterator = resultAsSet.iterator();

		NormalizedSource oneResult = iterator.next();
		Assertions.assertEquals(oneResult.name().get(), "one");

		NormalizedSource twoResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) twoResult).labels(),
				Collections.singletonMap("one", "1"));

		NormalizedSource threeResult = iterator.next();
		Assertions.assertEquals(threeResult.name().get(), "application");

		NormalizedSource fourResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) fourResult).labels(),
				Collections.singletonMap("two", "2"));

		NormalizedSource fiveResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) fiveResult).labels(),
				Collections.singletonMap("three", "3"));
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
	 * a config as above will result in a NormalizedSource where prefix is empty
	 */
	@Test
	void testUseNameAsPrefixUnsetEmptySources() {

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(), List.of(), true,
				"secret-a", "namespace", false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertSame(((NamedSecretNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT);
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
	 * a config as above will result in a NormalizedSource where prefix is empty, even if
	 * "useNameAsPrefix: true", because sources are empty
	 */
	@Test
	void testUseNameAsPrefixSetEmptySources() {

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(), List.of(), true,
				"secret-a", "namespace", true, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertSame(((NamedSecretNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT,
				"empty sources must generate a List with a single NormalizedSource, where prefix is unset,"
						+ "no matter of 'spring.cloud.kubernetes.secret.useNameAsPrefix' value");
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
	 * a config as above will result in a NormalizedSource where prefix will be equal to
	 * the secret name
	 */
	@Test
	void testUseNameAsPrefixUnsetNonEmptySources() {

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source("secret-one", "spring-k8s", Map.of(),
				null, true, false);

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(), List.of(one), true,
				"secret-one", null, false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "a single NormalizedSource is expected");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix().prefixProvider().get(),
				"secret-one");
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

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source("secret-one", "spring-k8s", Map.of(),
				null, false, false);

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source("secret-two", "spring-k8s", Map.of(),
				null, true, false);

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source("secret-three", "spring-k8s",
				Map.of(), null, true, false);

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(),
				List.of(one, two, three), true, "secret-one", null, false, true, false, RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 3, "3 NormalizedSources are expected");

		Assertions.assertSame(((NamedSecretNormalizedSource) sources.get(0)).prefix(), ConfigUtils.Prefix.DEFAULT);
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(1)).prefix().prefixProvider().get(),
				"secret-two");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(2)).prefix().prefixProvider().get(),
				"secret-three");
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
	 *          - name: secret-one
	 *            useNameAsPrefix: false
	 *            explicitPrefix: one
	 *          - name: secret-two
	 *            useNameAsPrefix: true
	 *            explicitPrefix: two
	 *          - name: secret-three
	 *            explicitPrefix: three
	 *          - name: secret-four
	 * </pre>
	 *
	 */
	@Test
	void testMultipleCases() {

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source("secret-one", "spring-k8s", Map.of(),
				"one", false, false);

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source("secret-two", "spring-k8s", Map.of(),
				"two", true, false);

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source("secret-three", "spring-k8s",
				Map.of(), "three", false, false);

		SecretsConfigProperties.Source four = new SecretsConfigProperties.Source("secret-four", "spring-k8s", Map.of(),
				null, false, false);

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(),
				List.of(one, two, three, four), true, "secret-one", "spring-k8s", false, false, false,
				RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 4, "4 NormalizedSources are expected");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix().prefixProvider().get(), "one");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(1)).prefix().prefixProvider().get(), "two");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(2)).prefix().prefixProvider().get(),
				"three");
		Assertions.assertSame(((NamedSecretNormalizedSource) sources.get(3)).prefix(), ConfigUtils.Prefix.DEFAULT);
	}

	/**
	 * <pre>
	 * spring:
	 *	cloud:
	 *    kubernetes:
	 *      secrets:
	 *        useNameAsPrefix: false
	 *        namespace: spring-k8s
	 *        includeProfileSpecificSources: false
	 *        sources:
	 *          - labels:
	 *              - name: first-label
	 *                value: secret-one
	 *            useNameAsPrefix: false
	 *            explicitPrefix: one
	 *          - labels:
	 *          	- name: second-label
	 * 	          	  value: secret-two
	 * 	          includeProfileSpecificSources: true
	 *            useNameAsPrefix: true
	 *            explicitPrefix: two
	 *          - labels:
	 *          	- name: third-label
	 * 	          	  value: secret-three
	 *            explicitPrefix: three
	 *          - labels:
	 * 	         	- name: fourth-label
	 * 	           	  value: secret-four
	 * </pre>
	 *
	 */
	@Test
	void testLabelsMultipleCases() {

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source(null, "spring-k8s",
				Map.of("first-label", "secret-one"), "one", false, false);

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source(null, "spring-k8s",
				Map.of("second-label", "secret-two"), "two", true, true);

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source(null, "spring-k8s",
				Map.of("third-label", "secret-three"), "three", false, false);

		SecretsConfigProperties.Source four = new SecretsConfigProperties.Source(null, "spring-k8s",
				Map.of("fourth-label", "secret-four"), null, false, false);

		SecretsConfigProperties properties = new SecretsConfigProperties(false, Map.of(), List.of(),
				List.of(one, two, three, four), false, null, "spring-k8s", false, false, false,
				RetryProperties.DEFAULT);

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		// we get 8 property sources, since "named" ones with "application" are
		// duplicated.
		// that's OK, since later in the code we get a LinkedHashSet out of them all,
		// so they become 5 only.
		Assertions.assertEquals(sources.size(), 8, "4 NormalizedSources are expected");

		LabeledSecretNormalizedSource labeled1 = (LabeledSecretNormalizedSource) sources.get(1);
		Assertions.assertEquals(labeled1.prefix().prefixProvider().get(), "one");
		Assertions.assertFalse(labeled1.profileSpecificSources());

		LabeledSecretNormalizedSource labeled3 = (LabeledSecretNormalizedSource) sources.get(3);
		Assertions.assertEquals(labeled3.prefix().prefixProvider().get(), "two");
		Assertions.assertTrue(labeled3.profileSpecificSources());

		LabeledSecretNormalizedSource labeled5 = (LabeledSecretNormalizedSource) sources.get(5);
		Assertions.assertEquals(labeled5.prefix().prefixProvider().get(), "three");
		Assertions.assertFalse(labeled5.profileSpecificSources());

		LabeledSecretNormalizedSource labeled7 = (LabeledSecretNormalizedSource) sources.get(7);
		Assertions.assertSame(labeled7.prefix(), ConfigUtils.Prefix.DEFAULT);
		Assertions.assertFalse(labeled7.profileSpecificSources());

		Set<NormalizedSource> set = new LinkedHashSet<>(sources);
		Assertions.assertEquals(5, set.size());
	}

}
