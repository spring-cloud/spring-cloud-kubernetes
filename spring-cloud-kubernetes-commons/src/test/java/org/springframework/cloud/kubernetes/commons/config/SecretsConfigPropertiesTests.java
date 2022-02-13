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
		Assertions.assertEquals(((NamedSecretNormalizedSource) source.get(0)).name(), "application");
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
		Assertions.assertEquals(((NamedSecretNormalizedSource) oneResult).name(), "one");

		NormalizedSource twoResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) twoResult).labels(),
				Collections.singletonMap("one", "1"));

		NormalizedSource threeResult = iterator.next();
		Assertions.assertEquals(((NamedSecretNormalizedSource) threeResult).name(), "application");

		NormalizedSource fourResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) fourResult).labels(),
				Collections.singletonMap("two", "2"));

		NormalizedSource fiveResult = iterator.next();
		Assertions.assertEquals(((LabeledSecretNormalizedSource) fiveResult).labels(),
				Collections.singletonMap("three", "3"));

	}

}
