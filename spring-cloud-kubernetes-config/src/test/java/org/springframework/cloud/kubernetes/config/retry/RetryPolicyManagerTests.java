/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config.retry;

import java.util.HashMap;
import java.util.stream.IntStream;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.config.ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.config.ConfigMapUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Andres Navidad
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.bootstrap.name=retrypolicy" })
@AutoConfigureWebTestClient
public class RetryPolicyManagerTests {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired
	private ConfigMapConfigProperties configMapConfigProperties;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");

		int numOfConfigMap = 6;

		IntStream.rangeClosed(1, numOfConfigMap).forEach(i -> {
			ConfigMapUtils.createConfigmap(server, "name" + i, "ns" + i,
					new HashMap<String, String>() {
						{
							put("some.message", "value" + i);
						}
					});
		});
	}

	@Test
	public void testConfigMapNotExistWithCustomPolicy() {

		ConfigMapPropertySource cfmps = new ConfigMapPropertySource(mockClient, "name2",
				"not_exist", new StandardEnvironment(), new RetryPolicy(5, 100));

		Assert.assertEquals(0, cfmps.getPropertyNames().length);
	}

	@Test
	public void testNormalizeRetryPolicy() {

		ConfigMapPropertySource cmps = new ConfigMapPropertySource(mockClient, "name3",
				"ns3", new StandardEnvironment(),
				configMapConfigProperties.getRetryPolicy());

		Assert.assertEquals(1, cmps.getPropertyNames().length);
		Assert.assertEquals("value3", cmps.getProperty("some.message"));

		RetryPolicy retryPolicy = new RetryPolicy(1000, -1);
		RetryPolicy normalizedRP = RetryPolicyManager.normalizeRetryPolicy(retryPolicy);

		Assert.assertEquals(1000, normalizedRP.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP.getDelay());

		RetryPolicy retryPolicy2 = new RetryPolicy(0, -1);
		RetryPolicy normalizedRP2 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy2);

		Assert.assertEquals(1, normalizedRP2.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP2.getDelay());

		RetryPolicy retryPolicy3 = new RetryPolicy(1, -1);
		RetryPolicy normalizedRP3 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy3);

		Assert.assertEquals(1, normalizedRP3.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP3.getDelay());

		RetryPolicy retryPolicy4 = new RetryPolicy(-3, -1);
		RetryPolicy normalizedRP4 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy4);

		Assert.assertEquals(-3, normalizedRP4.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP4.getDelay());

		RetryPolicy retryPolicy5 = new RetryPolicy(-3, -5);
		RetryPolicy normalizedRP5 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy5);

		Assert.assertEquals(-3, normalizedRP5.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP5.getDelay());

		RetryPolicy retryPolicy6 = new RetryPolicy(-3, 0);
		RetryPolicy normalizedRP6 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy6);

		Assert.assertEquals(-3, normalizedRP6.getMaxAttempts());
		Assert.assertEquals(0, normalizedRP6.getDelay());

		RetryPolicy retryPolicy7 = new RetryPolicy(-3, 5);
		RetryPolicy normalizedRP7 = RetryPolicyManager.normalizeRetryPolicy(retryPolicy7);

		Assert.assertEquals(-3, normalizedRP7.getMaxAttempts());
		Assert.assertEquals(5, normalizedRP7.getDelay());
	}

}
