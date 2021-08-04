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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author wind57
 */
public class ConfigMapConfigPropertiesTests {

	/*
	 * when "spring.cloud.kubernetes.config.sources" are empty, we need to have the
	 * value of "useNameAsPrefix" as false.
	 */
	@Test
	public void testUseNameAsPrefixUnsetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(
			sources.size(), 1,
			"empty sources must generate a List with a single NormalizedSource"
		);

		Assertions.assertFalse(
			sources.get(0).isUseNameAsPrefix(),
			"empty sources must generate a List with a single NormalizedSource, where useNameAsPrefix must be false"
		);
	}

	/*
	 * when "spring.cloud.kubernetes.config.sources" are empty, we need to have the
	 * value of "useNameAsPrefix" as false; even if "spring.cloud.kubernetes.config.useNameAsPrefix"
	 * is set to true.
	 * It makes no sense to prefix config map properties when a single config map exists
	 */
	@Test
	public void testUseNameAsPrefixSetEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setSources(Collections.emptyList());
		properties.setUseNameAsPrefix(true);

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(
			sources.size(), 1,
			"empty sources must generate a List with a single NormalizedSource"
		);

		Assertions.assertFalse(
			sources.get(0).isUseNameAsPrefix(),
			"empty sources must generate a List with a single NormalizedSource, where useNameAsPrefix must be false" +
				", no matter of 'spring.cloud.kubernetes.config.useNameAsPrefix' value"
		);
	}

	/*
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
	 * a config as above will result in a NormalizedSource where useNameAsPrefix will be set to true
	 */
	@Test
	public void testUseNameAsPrefixUnsetNonEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(true);

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("name");
		one.setNamespace("namespace");
		properties.setSources(Collections.singletonList(one));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(
			sources.size(), 1,
			"a single NormalizedSource is expected"
		);

		Assertions.assertTrue(
			sources.get(0).isUseNameAsPrefix(),
			"useNameAsPrefix must be taken from 'spring.cloud.kubernetes.config.useNameAsPrefix'"
		);
	}

	/*
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
	 * will override 'spring.cloud.kubernetes.config.useNameAsPrefix'.
	 * For the last entry in sources, since there is no explicit 'useNameAsPrefix', the one from
	 * 'spring.cloud.kubernetes.config.useNameAsPrefix' will be taken.
	 */
	@Test
	public void testUseNameAsPrefixSetNonEmptySources() {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setUseNameAsPrefix(true);

		ConfigMapConfigProperties.Source one = new ConfigMapConfigProperties.Source();
		one.setName("nameOne");
		one.setNamespace("namespaceOne");
		one.setUseNameAsPrefix(false);

		ConfigMapConfigProperties.Source two = new ConfigMapConfigProperties.Source();
		two.setName("nameTwo");
		two.setNamespace("namespaceTwo");
		two.setUseNameAsPrefix(true);

		ConfigMapConfigProperties.Source three = new ConfigMapConfigProperties.Source();
		three.setName("nameTwo");
		three.setNamespace("namespaceTwo");

		properties.setSources(Arrays.asList(one, two, three));

		List<ConfigMapConfigProperties.NormalizedSource> sources = properties.determineSources();
		Assertions.assertEquals(
			sources.size(), 3,
			"3 NormalizedSources are expected"
		);

		Assertions.assertFalse(sources.get(0).isUseNameAsPrefix());
		Assertions.assertTrue(sources.get(1).isUseNameAsPrefix());
		Assertions.assertTrue(sources.get(2).isUseNameAsPrefix());
	}

}
