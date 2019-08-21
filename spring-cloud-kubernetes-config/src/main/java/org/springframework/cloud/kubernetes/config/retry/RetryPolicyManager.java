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

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

/**
 * Manager to perform retry polices.
 *
 * @author Andres Navidad
 */
public final class RetryPolicyManager {

	private static final Log LOG = LogFactory.getLog(RetryPolicyManager.class);

	private RetryPolicyManager() {
	}

	public static ConfigMap tryRecoverConfigMap(KubernetesClient client, String name,
			String namespace, RetryPolicy retryPolicy) {

		RetryPolicy normalizedRetryPolicy = normalizeRetryPolicy(retryPolicy);

		String configMapMessage = "configMap with name: [" + name + "] in namespace:["
				+ namespace + "]";
		RetryTemplate template = createRetryTemplate(normalizedRetryPolicy);

		return template.execute(
				(RetryCallback<ConfigMap, KubernetesClientException>) retryContext -> {

					LOG.warn("Try to read " + configMapMessage + ". Try "
							+ (retryContext.getRetryCount() + 1) + " of "
							+ normalizedRetryPolicy.getMaxAttempts() + " ...");
					return StringUtils.isEmpty(namespace)
							? client.configMaps().withName(name).get()
							: client.configMaps().inNamespace(namespace).withName(name)
									.get();
				}, recoverContext -> {
					LOG.warn("Reached the maximum number of attempts to read" + " "
							+ configMapMessage);
					return new ConfigMap();
				});
	}

	public static RetryPolicy normalizeRetryPolicy(RetryPolicy retryPolicy) {

		LOG.debug("Your current retry policy is: " + retryPolicy);

		RetryPolicy retryPolicyOpt = Optional.ofNullable(retryPolicy)
				.orElseGet(RetryPolicy::new);

		int maxAttempts = retryPolicyOpt.getMaxAttempts() == 0 ? 1
				: retryPolicyOpt.getMaxAttempts();

		long delay = retryPolicyOpt.getDelay() < 0 ? 0 : retryPolicyOpt.getDelay();

		RetryPolicy normalizedRetryPolicy = new RetryPolicy(maxAttempts, delay);
		LOG.debug("Normalized retry policy: " + normalizedRetryPolicy);
		return normalizedRetryPolicy;
	}

	private static RetryTemplate createRetryTemplate(RetryPolicy retryPolicy) {

		SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
		simpleRetryPolicy.setMaxAttempts(retryPolicy.getMaxAttempts());

		FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
		backOffPolicy.setBackOffPeriod(retryPolicy.getDelay());

		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(simpleRetryPolicy);
		template.setBackOffPolicy(backOffPolicy);

		return template;
	}

}
