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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.client")
public class KubernetesClientProperties {

    private Boolean trustCerts;
    private String masterUrl;
    private String apiVersion;
    private String namespace;
    private String caCertFile;
    private String caCertData;
    private String clientCertFile;
    private String clientCertData;
    private String clientKeyFile;
    private String clientKeyData;
    private String clientKeyAlgo;
    private String clientKeyPassphrase;
    private String username;
    private String password;
    private Integer watchReconnectInterval;
    private Integer watchReconnectLimit;
    private Integer connectionTimeout;
    private Integer requestTimeout;
    private Long rollingTimeout;
    private Integer loggingInterval;

    public Boolean isTrustCerts() {
        return trustCerts;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCaCertFile() {
        return caCertFile;
    }

    public String getCaCertData() {
        return caCertData;
    }

    public String getClientCertFile() {
        return clientCertFile;
    }

    public String getClientCertData() {
        return clientCertData;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public String getClientKeyData() {
        return clientKeyData;
    }

    public String getClientKeyAlgo() {
        return clientKeyAlgo;
    }

    public String getClientKeyPassphrase() {
        return clientKeyPassphrase;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Integer getWatchReconnectInterval() {
        return watchReconnectInterval;
    }

    public Integer getWatchReconnectLimit() {
        return watchReconnectLimit;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public Long getRollingTimeout() {
        return rollingTimeout;
    }

    public Integer getLoggingInterval() {
        return loggingInterval;
    }
}
