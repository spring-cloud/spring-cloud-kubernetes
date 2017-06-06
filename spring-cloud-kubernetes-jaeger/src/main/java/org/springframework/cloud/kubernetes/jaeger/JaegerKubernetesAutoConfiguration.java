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

import java.net.URL;
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
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.spring.web.autoconfig.TracerAutoConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@AutoConfigureBefore(TracerAutoConfiguration.class)
@EnableConfigurationProperties(KubernetesJaegerDiscoveryProperties.class)
public class JaegerKubernetesAutoConfiguration {

	private static final Log LOG = LogFactory.getLog(JaegerKubernetesAutoConfiguration.class);

    @Bean
    public Tracer tracer(KubernetesClient client, KubernetesJaegerDiscoveryProperties discoveryProperties) throws Exception {

    	String serviceName = discoveryProperties.getServiceName();
    	String traceServerName = discoveryProperties.getTracerServerName();
		String serviceNamespace = Utils.isNotNullOrEmpty(discoveryProperties.getServiceNamespace()) ? discoveryProperties.getServiceNamespace() : client.getNamespace();

        List<ServiceInstance> services = getInstances(client, traceServerName, serviceNamespace);
        String serviceUrl = services.stream()
                .findFirst()
                .map(s -> s.getUri().toString())
                .orElse(null);

        if (serviceUrl == null || serviceUrl.isEmpty()) {
			LOG.info("Jaeger k8s starter creating Noop Tracer");
            return NoopTracerFactory.create();
        }

        return  jaegerTracer(serviceUrl, serviceName);
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

	private Tracer jaegerTracer(String serviceURL, String serviceName) throws Exception {
		URL url = new URL(serviceURL);
		Sender sender = new UdpSender(url.getHost(), url.getPort(), 0);
		return new com.uber.jaeger.Tracer.Builder(serviceName,
			new RemoteReporter(sender, 100, 50,
				new Metrics(new StatsFactoryImpl(new NullStatsReporter()))),
			new ProbabilisticSampler(1.0))
			.build();
	}

}
