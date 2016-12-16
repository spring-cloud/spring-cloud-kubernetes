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

package io.fabric8.spring.cloud.kubernetes.archaius;

import com.netflix.config.DynamicWatchedConfiguration;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import java.util.Map;

class ArchaiusConfigMapSourceRegistar implements ImportBeanDefinitionRegistrar {

    private static final String KUBERNETES_CLIENT_REF = "kubernetesClient";

    private static final String VALUE_ATTR = "value";
    private static final String NAME_ATTR = "name";
    private static final String NAMESPACE_ATTR = "namespace";

    private static final String CONFIG_MAP_SOURCE_SUFFIX = ".ConfigMapSourceConfiguration";
    private static final String DYNAMIC_WATCH_CONFIG_SUFFIX = ".DynamicWatchedConfiguration";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {

        Map<String, Object> source = metadata.getAnnotationAttributes(ArchaiusConfigMapSource.class.getName(), true);
        String name = getSourceName(source);
        String namespace = getSourceNamespace(source);
        if (name != null) {
            registerSourceConfiguration(registry, name, namespace);
        }
    }

    private String getSourceName(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        String value = (String) source.get(VALUE_ATTR);
        if (!StringUtils.hasText(value)) {
            value = (String) source.get(NAME_ATTR);
        }
        if (StringUtils.hasText(value)) {
            return value;
        }
        throw new IllegalStateException(
                "Either 'name' or 'value' must be provided in @ConfigMapSource");
    }

    private String getSourceNamespace(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        String namespace = (String) source.get(NAMESPACE_ATTR);
        if (StringUtils.hasText(namespace)) {
            return namespace;
        }
        return null;
    }

    private void registerSourceConfiguration(BeanDefinitionRegistry registry, Object name, Object namespace) {
        BeanDefinitionBuilder configMapSourceConfigBuilder = BeanDefinitionBuilder.genericBeanDefinition(ArchaiusConfigMapSourceConfiguration.class);
        BeanDefinitionBuilder dynamicWatchedConfigBuilder = BeanDefinitionBuilder.genericBeanDefinition(DynamicWatchedConfiguration.class);

        configMapSourceConfigBuilder.addConstructorArgReference(KUBERNETES_CLIENT_REF);
        configMapSourceConfigBuilder.addConstructorArgValue(name);
        configMapSourceConfigBuilder.addConstructorArgValue(namespace);
        String configMapSourceConfigName = name + CONFIG_MAP_SOURCE_SUFFIX;
        registry.registerBeanDefinition(configMapSourceConfigName,  configMapSourceConfigBuilder.getBeanDefinition());

        String dynamicWatchedConfigName = name + DYNAMIC_WATCH_CONFIG_SUFFIX;
        dynamicWatchedConfigBuilder.addConstructorArgReference(configMapSourceConfigName);
        registry.registerBeanDefinition(dynamicWatchedConfigName, dynamicWatchedConfigBuilder.getBeanDefinition());
    }
}