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

/**
 * This is a interface to make possible a retry policy to get external configuration of
 * Kubernetes' ConfigMap.
 *
 * Default implementation is supplied by
 * {@link org.springframework.cloud.kubernetes.config.retry.DefaultRetryPolicy}
 *
 * Define your own implementation to replace default implementation if necessary.
 *
 * @author Andres Navidad
 */
public interface RetryPolicyOperations {

	ConfigMap tryRecoverConfigMap(KubernetesClient client, String name, String namespace);

}
