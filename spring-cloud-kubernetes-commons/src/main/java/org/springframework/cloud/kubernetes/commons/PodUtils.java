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

package org.springframework.cloud.kubernetes.commons;

import java.util.function.Supplier;

/**
 * Utility interface to retrieve Pod related information.
 *
 * @author Ioannis Canellos
 */
public interface PodUtils<T> {

	/**
	 * @return A supplier of the currentPod Pod. The supplier will hold the currentPod pod
	 * if inside Kubernetes or false, otherwise.
	 */
	Supplier<T> currentPod();

	/**
	 * @return true if called from within Kubernetes, false otherwise.
	 */
	Boolean isInsideKubernetes();

}
