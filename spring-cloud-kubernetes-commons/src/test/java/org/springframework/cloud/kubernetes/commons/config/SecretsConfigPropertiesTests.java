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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class SecretsConfigPropertiesTests {

	private final SecretsConfigProperties properties = new SecretsConfigProperties();

	/**
	 * the case when labels are empty
	 */
	@Test
	void emptySourcesSecretName() {
		properties.setNamespace("namespace");
		List<NormalizedSource> source = properties.determineSources(new MockEnvironment());
		properties.setSources(Collections.emptyList());
		Assertions.assertEquals(source.size(), 1);
		Assertions.assertTrue(source.get(0) instanceof NamedSecretNormalizedSource);
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
		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setNamespace("spring-k8s");
		one.setName("one");
		one.setLabels(Collections.singletonMap("one", "1"));

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source();
		two.setLabels(Collections.singletonMap("two", "2"));
		two.setNamespace("spring-k8s");

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source();
		three.setLabels(Collections.singletonMap("three", "3"));
		three.setNamespace("spring-k8s");

		properties.setSources(Arrays.asList(one, two, three));

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
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setName("secret-a");
		properties.setNamespace("spring-k8s");

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix(), "",
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
	 * a config as above will result in a NormalizedSource where prefix is empty, even if
	 * "useNameAsPrefix: true", because sources are empty
	 */
	@Test
	void testUseNameAsPrefixSetEmptySources() {
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setUseNameAsPrefix(true);
		properties.setName("secret-a");
		properties.setNamespace("spring-k8s");

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "empty sources must generate a List with a single NormalizedSource");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix(), "",
				"empty sources must generate a List with a single NormalizedSource, where prefix is empty,"
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
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setUseNameAsPrefix(true);
		properties.setNamespace("spring-k8s");

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setName("secret-one");
		properties.setSources(Collections.singletonList(one));

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 1, "a single NormalizedSource is expected");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix(), "secret-one");
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

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 3, "3 NormalizedSources are expected");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix(), "");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(1)).prefix(), "secret-two");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(2)).prefix(), "secret-three");
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
		SecretsConfigProperties properties = new SecretsConfigProperties();
		properties.setUseNameAsPrefix(false);
		properties.setNamespace("spring-k8s");

		SecretsConfigProperties.Source one = new SecretsConfigProperties.Source();
		one.setNamespace("secret-one");
		one.setUseNameAsPrefix(false);
		one.setExplicitPrefix("one");

		SecretsConfigProperties.Source two = new SecretsConfigProperties.Source();
		two.setNamespace("secret-two");
		two.setUseNameAsPrefix(true);
		two.setExplicitPrefix("two");

		SecretsConfigProperties.Source three = new SecretsConfigProperties.Source();
		three.setNamespace("secret-three");
		three.setExplicitPrefix("three");

		SecretsConfigProperties.Source four = new SecretsConfigProperties.Source();
		four.setNamespace("secret-four");

		properties.setSources(Arrays.asList(one, two, three, four));

		List<NormalizedSource> sources = properties.determineSources(new MockEnvironment());
		Assertions.assertEquals(sources.size(), 4, "4 NormalizedSources are expected");

		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(0)).prefix(), "one");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(1)).prefix(), "two");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(2)).prefix(), "three");
		Assertions.assertEquals(((NamedSecretNormalizedSource) sources.get(3)).prefix(), "");
	}

}
