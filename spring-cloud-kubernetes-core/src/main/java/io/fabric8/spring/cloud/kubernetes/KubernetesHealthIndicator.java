/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.fabric8.spring.cloud.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.nio.file.Paths;

public class KubernetesHealthIndicator extends AbstractHealthIndicator {

    private static final String HOSTNAME = "HOSTNAME";

    private String hostName;
    private KubernetesClient client;

    public KubernetesHealthIndicator(KubernetesClient client) {
        this.client = client;
        this.hostName = System.getenv(HOSTNAME);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            Pod current = getCurrentPod();
            if (current != null) {
                builder.up()
                        .withDetail("internal", true)
                        .withDetail("podName", hostName)
                        .withDetail("podIp", current.getStatus().getPodIP())
                        .withDetail("serviceAccount", current.getSpec().getServiceAccountName())
                        .withDetail("nodeName", current.getSpec().getNodeName())
                        .withDetail("hostIp", current.getStatus().getHostIP());
            } else {
                builder.up()
                        .withDetail("internal", false);
            }
        } catch (Exception e) {
            builder.down(e);
        }
    }


    private Pod getCurrentPod() {
        if (isServiceAccountFound() && isHostNameEnvVarPresent()) {
            return client.pods().withName(hostName).get();
        } else {
            return null;
        }
    }

    private boolean isHostNameEnvVarPresent() {
        return hostName != null && !hostName.isEmpty();
    }

    private boolean isServiceAccountFound() {
        return Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).toFile().exists() &&
                Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).toFile().exists();
    }
}