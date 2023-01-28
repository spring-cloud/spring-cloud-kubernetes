/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false" })
class KubernetesClientKubernetesDiscoveryEnabledTests {

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void testBeansPresent() {
		Assertions.assertEquals(context.getBeansOfType(KubernetesCatalogWatch.class).size(), 1);
		Assertions.assertEquals(
				context.getBeansOfType(KubernetesDiscoveryClientHealthIndicatorInitializer.class).size(), 1);
		Assertions.assertEquals(context.getBeansOfType(KubernetesInformerDiscoveryClient.class).size(), 1);
	}

}
