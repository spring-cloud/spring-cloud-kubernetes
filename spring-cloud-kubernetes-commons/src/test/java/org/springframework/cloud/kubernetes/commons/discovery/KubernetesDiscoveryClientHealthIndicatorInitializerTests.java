/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource;

/**
 * @author wind57
 *
 * Tests the
 * {@link org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer}
 * with the fabric8 client.
 *
 */
class KubernetesDiscoveryClientHealthIndicatorInitializerTests {

	private static ApplicationEventPublisher publisher;

	// we don't really need an actual Pod here (fabric8 or k8s-native), but only
	// "something" we can assert for.
	private static final Object POD = Mockito.mock(Object.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(publisher, POD);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testInstanceRegistrationEventPublishedWhenInsideK8s() {
		new ApplicationContextRunner()
				.withUserConfiguration(InstanceRegistrationEventPublishedInsideK8sConfiguration.class)
				.run(context -> assertThat(context).hasSingleBean(PodUtils.class));

		ArgumentCaptor<InstanceRegisteredEvent<RegisteredEventSource>> captor = ArgumentCaptor
				.forClass(InstanceRegisteredEvent.class);
		Mockito.verify(publisher, Mockito.times(1)).publishEvent(captor.capture());
		KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource source = (KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource) captor
				.getValue().getSource();
		assertThat(source.cloudPlatform()).isEqualTo("kubernetes");
		assertThat(source.inside()).isTrue();
		assertThat(source.pod()).isSameAs(POD);

	}

	@Test
	@SuppressWarnings("unchecked")
	void testInstanceRegistrationEventPublishedWhenOutsideK8s() {
		new ApplicationContextRunner()
				.withUserConfiguration(InstanceRegistrationEventPublishedOutsideK8sConfiguration.class)
				.run(context -> assertThat(context).hasSingleBean(PodUtils.class));

		ArgumentCaptor<InstanceRegisteredEvent<RegisteredEventSource>> captor = ArgumentCaptor
				.forClass(InstanceRegisteredEvent.class);
		Mockito.verify(publisher, Mockito.times(1)).publishEvent(captor.capture());
		KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource source = (KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource) captor
				.getValue().getSource();
		assertThat(source.cloudPlatform()).isEqualTo("kubernetes");
		assertThat(source.inside()).isFalse();
		assertThat(source.pod()).isNotNull();

	}

	@Configuration
	static class InstanceRegistrationEventPublishedInsideK8sConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		PodUtils<Object> podUtils() {
			PodUtils<Object> podUtils = Mockito.mock(PodUtils.class);
			Mockito.when(podUtils.isInsideKubernetes()).thenReturn(true);
			Mockito.when(podUtils.currentPod()).thenReturn(() -> POD);
			return podUtils;
		}

		@Bean
		@Primary
		ApplicationEventPublisher publisher() {
			publisher = Mockito.mock(ApplicationEventPublisher.class);
			return publisher;
		}

		@Bean
		KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(PodUtils<Object> podUtils,
				ApplicationEventPublisher publisher) {
			return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, publisher);
		}

	}

	@Configuration
	static class InstanceRegistrationEventPublishedOutsideK8sConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		PodUtils<Object> podUtils() {
			PodUtils<Object> podUtils = Mockito.mock(PodUtils.class);
			Mockito.when(podUtils.isInsideKubernetes()).thenReturn(false);
			Mockito.when(podUtils.currentPod()).thenReturn(() -> POD);
			return podUtils;
		}

		@Bean
		@Primary
		ApplicationEventPublisher publisher() {
			publisher = Mockito.mock(ApplicationEventPublisher.class);
			return publisher;
		}

		@Bean
		KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(PodUtils<Object> podUtils,
				ApplicationEventPublisher publisher) {
			return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, publisher);
		}

	}

}
