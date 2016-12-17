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

package org.springframework.cloud.kubernetes;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import io.fabric8.kubernetes.api.model.Pod;

public class KubernetesHealthIndicator extends AbstractHealthIndicator {

    private PodUtils utils;

    public KubernetesHealthIndicator(PodUtils utils) {
        this.utils = utils;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            Pod current = utils.currentPod().get();
            if (current != null) {
                builder.up()
                        .withDetail("inside", true)
                        .withDetail("namespace", current.getMetadata().getNamespace())
                        .withDetail("podName", current.getMetadata().getName())
                        .withDetail("podIp", current.getStatus().getPodIP())
                        .withDetail("serviceAccount", current.getSpec().getServiceAccountName())
                        .withDetail("nodeName", current.getSpec().getNodeName())
                        .withDetail("hostIp", current.getStatus().getHostIP());
            } else {
                builder.up()
                        .withDetail("inside", false);
            }
        } catch (Exception e) {
            builder.down(e);
        }
    }
}
