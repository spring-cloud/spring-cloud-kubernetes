/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.jaeger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;
import io.opentracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@EnableConfigurationProperties(KubernetesJaegerDiscoveryProperties.class)
public class JaegerKubernetesAutoConfiguration {

	@Autowired
	private Tracer noopTracer;

	// TODO - Check how we can return noopTracer and avoid
	/**
	 * ***************************
	 APPLICATION FAILED TO START
	 ***************************

	 Description:

	 Parameter 0 of method tracingFilter in io.opentracing.contrib.spring.web.autoconfig.ServerTracingAutoConfiguration required a single bean, but 2 were found:
	 - noopTracer: defined by method 'noopTracer' in class path resource [io/opentracing/contrib/spring/web/autoconfig/TracerAutoConfiguration.class]
	 - reporter: defined by method 'reporter' in class path resource [org/springframework/cloud/kubernetes/jaeger/JaegerKubernetesAutoConfiguration.class]


	 Action:

	 Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
	 */

    @Bean
    public Tracer reporter(KubernetesClient client, KubernetesJaegerDiscoveryProperties discoveryProperties) {

    	String serviceName = discoveryProperties.getServiceName();
    	String traceServerName = discoveryProperties.getTracerServerName();
		String serviceNamespace = Utils.isNotNullOrEmpty(discoveryProperties.getServiceNamespace()) ? discoveryProperties.getServiceNamespace() : client.getNamespace();

        List<ServiceInstance> services = getInstances(client, traceServerName, serviceNamespace);
        String serviceUrl = services.stream()
                .findFirst()
                .map(s -> s.getUri().toString())
                .orElse(null);

        return serviceUrl == null || serviceUrl.isEmpty()
                ? noopTracer
                : jaegerTracer(serviceUrl, serviceName);
    }

    private static List<ServiceInstance> getInstances(KubernetesClient client, String name, String namespace) {
        Assert.notNull(name, "[Assertion failed] - the service name must not be null");

        return Optional.ofNullable(client.endpoints().inNamespace(namespace).withName(name).get())
                .orElse(new Endpoints())
                .getSubsets()
                .stream()
                .flatMap(s -> s.getAddresses().stream().map(a -> (ServiceInstance) new KubernetesServiceInstance(name, a ,s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new), false)))
                .collect(Collectors.toList());
    }

	private Tracer jaegerTracer(String url, String serviceName) {
		Sender sender = new UdpSender(url, 0, 0);
		return new com.uber.jaeger.Tracer.Builder(serviceName,
			new RemoteReporter(sender, 100, 50,
				new Metrics(new StatsFactoryImpl(new NullStatsReporter()))),
			new ProbabilisticSampler(1.0))
			.build();
	}

}
