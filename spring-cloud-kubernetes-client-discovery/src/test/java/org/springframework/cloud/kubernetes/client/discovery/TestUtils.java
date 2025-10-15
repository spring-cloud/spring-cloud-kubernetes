/*
 * Copyright 2013-present the original author or authors.
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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;

import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Some common class that test code delegates to.
 *
 * @author wind57
 */
final class TestUtils {

	private TestUtils() {

	}

	@SuppressWarnings("unchecked")
	static void assertInformerBeansPresent(AssertableApplicationContext context, int times) {
		String sharedInformerFactoriesBeanName = context
			.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<SharedInformerFactory>>() {
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

		String endpointsListersBeanName = context
			.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Endpoints>>>() {
			}))[0];
		List<Lister<V1Endpoints>> endpointsListers = (List<Lister<V1Endpoints>>) context
			.getBean(endpointsListersBeanName);
		assertThat(endpointsListers.size()).isEqualTo(times);
	}

	static void assertInformerBeansMissing(AssertableApplicationContext context) {
		String[] sharedInformerFactoriesBeanName = context
			.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<SharedInformerFactory>>() {
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

		String[] endpointsListersBeanName = context
			.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<Lister<V1Endpoints>>>() {
			}));
		assertThat(endpointsListersBeanName).isEmpty();
	}

	static void mockEndpointsAndServices(List<String> namespaces, WireMockExtension server) {
		namespaces.forEach(namespace -> {
			mockEndpointsCall(namespace, server);
			mockServicesCall(namespace, server);
		});
	}

	private static void mockEndpointsCall(String namespace, WireMockExtension server) {

		// watch=false, first call to populate watcher cache
		server.stubFor(WireMock.get(urlMatching("^/api/v1/namespaces/" + namespace + "/endpoints.*"))
			.withQueryParam("watch", equalTo("false"))
			.willReturn(WireMock.aResponse()
				.withStatus(200)
				.withBody(JSON.serialize(new V1EndpointsList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(new V1Endpoints().metadata(new V1ObjectMeta().namespace(namespace)))))));

		// watch=true, call to re-sync
		server.stubFor(WireMock.get(urlMatching("^/api/v1/namespaces/" + namespace + "/endpoints.*"))
			.withQueryParam("watch", WireMock.equalTo("true"))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

	private static void mockServicesCall(String namespace, WireMockExtension server) {

		// watch=false, first call to populate watcher cache
		server.stubFor(WireMock.get(urlMatching("^/api/v1/namespaces/" + namespace + "/services.*"))
			.withQueryParam("watch", equalTo("false"))
			.willReturn(WireMock.aResponse()
				.withStatus(200)
				.withBody(JSON.serialize(new V1ServiceList().metadata(new V1ListMeta().resourceVersion("0"))
					.addItemsItem(new V1Service().metadata(new V1ObjectMeta().namespace(namespace)))))));

		// watch=true, call to re-sync
		server.stubFor(WireMock.get(urlMatching("^/api/v1/namespaces/" + namespace + "/services.*"))
			.withQueryParam("watch", equalTo("true"))
			.willReturn(aResponse().withStatus(200).withBody("")));
	}

}
