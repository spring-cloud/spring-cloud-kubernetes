package org.springframework.cloud.kubernetes.istio;

import io.fabric8.kubernetes.client.Config;
import me.snowdrop.istio.client.DefaultIstioClient;
import me.snowdrop.istio.client.IstioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
public class IstioAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IstioClient istioClient(Config config) {
        return new DefaultIstioClient(config);
    }
}
