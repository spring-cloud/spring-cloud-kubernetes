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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.cloud.kubernetes.config.retry.RetryPolicyOperations}
 * implementation for recover ConfigMap external configuration.
 *
 * Out-of-box this class use RetryTemplate class from spring-retry module with a
 * SimpleRetryPolicy and FixedBackoffPolicy. This behaviour can be override defining your
 * own RetryTemplate bean.
 *
 * @author Andres Navidad
 */
public final class DefaultRetryPolicy implements RetryPolicyOperations {

	private static final Log LOG = LogFactory.getLog(DefaultRetryPolicy.class);

	private RetryTemplate retryTemplate;

	public DefaultRetryPolicy(RetryTemplate retryTemplate) {
		this.retryTemplate = retryTemplate;
	}

	public ConfigMap tryRecoverConfigMap(KubernetesClient client, String name,
			String namespace) {

		String configMapMessage = "configMap with name: [" + name + "] in namespace:["
				+ namespace + "]";

		return retryTemplate.execute(
				(RetryCallback<ConfigMap, KubernetesClientException>) retryContext -> {

					LOG.warn("Try to read " + configMapMessage + ". Try "
							+ (retryContext.getRetryCount() + 1));
					return StringUtils.isEmpty(namespace)
							? client.configMaps().withName(name).get()
							: client.configMaps().inNamespace(namespace).withName(name)
									.get();
				}, recoverContext -> {
					LOG.warn("Reached the maximum number of attempts to read" + " "
							+ configMapMessage);
					return null;
				});
	}

}
