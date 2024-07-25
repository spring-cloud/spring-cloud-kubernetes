/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.BaseOperation;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;
import org.mockito.Mockito;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class Fabric8LeaderApp {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
	KubernetesClient kubernetesClient() {
		KubernetesClient client = Mockito.mock(KubernetesClient.class);
		Mockito.when(client.getNamespace()).thenReturn("a");

		MixedOperation mixedOperation = Mockito.mock(MixedOperation.class);
		Mockito.when(client.configMaps()).thenReturn(mixedOperation);

		PodResource podResource = Mockito.mock(PodResource.class);
		Mockito.when(podResource.isReady()).thenReturn(true);

		Mockito.when(client.pods()).thenReturn(mixedOperation);
		Mockito.when(mixedOperation.withName(Mockito.anyString())).thenReturn(podResource);

		Resource resource = Mockito.mock(Resource.class);

		BaseOperation baseOperation = Mockito.mock(BaseOperation.class);
		Mockito.when(baseOperation.withName("leaders")).thenReturn(resource);

		Mockito.when(mixedOperation.inNamespace("a")).thenReturn(baseOperation);
		return client;
	}

	@Bean
	@Primary
	Lock lock() {
		return Mockito.mock(Lock.class);
	}

}
