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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Some common class that test code delegates to.
 *
 * @author wind57
 */
public final class TestUtils {

	private TestUtils() {

	}

	public static void assertSelectiveNamespacesBeansMissing(AssertableApplicationContext context) {
		String[] sharedInformerFactoriesBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedInformerFactory>>() {
				}));
		assertThat(sharedInformerFactoriesBeanName).isEmpty();

		String[] serviceSharedIndexInformersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedIndexInformer<V1Service>>>() {
				}));
		assertThat(serviceSharedIndexInformersBeanName).isEmpty();

		String[] endpointsSharedIndexInformersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedIndexInformer<V1Endpoints>>>() {
				}));
		assertThat(endpointsSharedIndexInformersBeanName).isEmpty();

		String[] serviceListersBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Service>>>() {
				}));
		assertThat(serviceListersBeanName).isEmpty();

		String[] endpointsListersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Endpoints>>>() {
				}));
		assertThat(endpointsListersBeanName).isEmpty();
	}

	@SuppressWarnings("unchecked")
	public static void assertSelectiveNamespacesBeansPresent(AssertableApplicationContext context, int times) {
		String sharedInformerFactoriesBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedInformerFactory>>() {
				}))[0];
		List<SharedInformerFactory> sharedInformerFactories = (List<SharedInformerFactory>) context
				.getBean(sharedInformerFactoriesBeanName);
		assertThat(sharedInformerFactories.size()).isEqualTo(times);

		String serviceSharedIndexInformersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedIndexInformer<V1Service>>>() {
				}))[0];
		List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers = (List<SharedIndexInformer<V1Service>>) context
				.getBean(serviceSharedIndexInformersBeanName);
		assertThat(serviceSharedIndexInformers.size()).isEqualTo(times);

		String endpointsSharedIndexInformersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<SharedIndexInformer<V1Endpoints>>>() {
				}))[0];
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers = (List<SharedIndexInformer<V1Endpoints>>) context
				.getBean(endpointsSharedIndexInformersBeanName);
		assertThat(endpointsSharedIndexInformers.size()).isEqualTo(times);

		String serviceListersBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Service>>>() {
				}))[0];
		List<Lister<V1Service>> serviceListers = (List<Lister<V1Service>>) context.getBean(serviceListersBeanName);
		assertThat(serviceListers.size()).isEqualTo(times);

		String endpointsListersBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Endpoints>>>() {
				}))[0];
		List<Lister<V1Endpoints>> endpointsListers = (List<Lister<V1Endpoints>>) context
				.getBean(endpointsListersBeanName);
		assertThat(endpointsListers.size()).isEqualTo(times);
	}

	@SuppressWarnings("unchecked")
	public static void assertNonSelectiveNamespacesBeansPresent(AssertableApplicationContext context) {
		assertThat(context).hasSingleBean(SharedInformerFactory.class);

		String serviceSharedIndexInformerBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<SharedIndexInformer<V1Service>>() {
				}))[0];
		SharedIndexInformer<V1Service> serviceSharedIndexInformer = (SharedIndexInformer<V1Service>) context
				.getBean(serviceSharedIndexInformerBeanName);
		assertThat(serviceSharedIndexInformer).isNotNull();

		String endpointSharedIndexInformerBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<SharedIndexInformer<V1Endpoints>>() {
				}))[0];
		SharedIndexInformer<V1Endpoints> endpointsSharedIndexInformer = (SharedIndexInformer<V1Endpoints>) context
				.getBean(endpointSharedIndexInformerBeanName);
		assertThat(endpointsSharedIndexInformer).isNotNull();

		String serviceListerBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<Lister<V1Service>>() {
				}))[0];
		Lister<V1Service> serviceLister = (Lister<V1Service>) context.getBean(serviceListerBeanName);
		assertThat(serviceLister).isNotNull();

		String endpointsListerBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<Lister<V1Endpoints>>() {
				}))[0];
		Lister<V1Endpoints> endpointsLister = (Lister<V1Endpoints>) context.getBean(endpointsListerBeanName);
		assertThat(endpointsLister).isNotNull();
	}

	public static void assertNonSelectiveNamespacesBeansMissing(AssertableApplicationContext context) {
		String[] serviceSharedIndexInformerBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<SharedIndexInformer<V1Service>>() {
				}));
		assertThat(serviceSharedIndexInformerBeanName).isEmpty();

		String[] endpointSharedIndexInformerBeanName = context.getBeanNamesForType(
				ResolvableType.forType(new ParameterizedTypeReference<SharedIndexInformer<V1Endpoints>>() {
				}));
		assertThat(endpointSharedIndexInformerBeanName).isEmpty();

		String[] serviceListerBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<Lister<V1Service>>() {
				}));
		assertThat(serviceListerBeanName).isEmpty();

		String[] endpointsListerBeanName = context
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<Lister<V1Endpoints>>() {
				}));
		assertThat(endpointsListerBeanName).isEmpty();
	}

}
