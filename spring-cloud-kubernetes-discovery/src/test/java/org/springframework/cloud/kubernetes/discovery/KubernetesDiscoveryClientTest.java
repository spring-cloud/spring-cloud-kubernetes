/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesDiscoveryClientTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private KubernetesDiscoveryProperties properties;

    @Mock
    private MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> serviceOperation;

    @InjectMocks
    private KubernetesDiscoveryClient underTest;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testFilteredServices() throws Exception {
        List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB");
        List<Service> services = createSpringBootServiceByName(springBootServiceNames);

        // Add non spring boot service
        Service service = new Service();
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName("ServiceNonSpringBoot");
        service.setMetadata(objectMeta);
        services.add(service);

        ServiceList serviceList = new ServiceList();
        serviceList.setItems(services);
        when(serviceOperation.list())
                .thenReturn(serviceList);
        when(kubernetesClient.services()).thenReturn(serviceOperation);

        when(properties.getFilter()).thenReturn("metadata.additionalProperties['spring-boot']");

        List<String> filteredServices = underTest.getServices();

        System.out.println("Filtered Services: " + filteredServices);
        assertEquals(springBootServiceNames, filteredServices);

    }

    @Test
    public void testFilteredServicesByPrefix() throws Exception {
        List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB", "serviceC");
        List<Service> services = createSpringBootServiceByName(springBootServiceNames);

        // Add non spring boot service
        Service service = new Service();
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName("anotherService");
        service.setMetadata(objectMeta);
        services.add(service);

        ServiceList serviceList = new ServiceList();
        serviceList.setItems(services);
        when(serviceOperation.list())
                .thenReturn(serviceList);
        when(kubernetesClient.services()).thenReturn(serviceOperation);

        when(properties.getFilter()).thenReturn("metadata.name.startsWith('service')");

        List<String> filteredServices = underTest.getServices();

        System.out.println("Filtered Services: " + filteredServices);
        assertEquals(springBootServiceNames, filteredServices);

    }

    @Test
    public void testNoExpression() throws Exception {
        List<String> springBootServiceNames = Arrays.asList("serviceA", "serviceB", "serviceC");
        List<Service> services = createSpringBootServiceByName(springBootServiceNames);

        ServiceList serviceList = new ServiceList();
        serviceList.setItems(services);
        when(serviceOperation.list())
                .thenReturn(serviceList);
        when(kubernetesClient.services()).thenReturn(serviceOperation);

        when(properties.getFilter()).thenReturn("");

        List<String> filteredServices = underTest.getServices();

        System.out.println("Filtered Services: " + filteredServices);
        assertEquals(springBootServiceNames, filteredServices);

    }

    private List<Service> createSpringBootServiceByName(List<String> serviceNames) {
        List<Service> serviceCollection = new ArrayList<>(serviceNames.size());
        for (String serviceName : serviceNames) {
            Service service = new Service();
            ObjectMeta objectMeta = new ObjectMeta();
            objectMeta.setName(serviceName);
            objectMeta.setAdditionalProperty("spring-boot", "true");
            service.setMetadata(objectMeta);
            serviceCollection.add(service);
        }
        return serviceCollection;
    }

}
