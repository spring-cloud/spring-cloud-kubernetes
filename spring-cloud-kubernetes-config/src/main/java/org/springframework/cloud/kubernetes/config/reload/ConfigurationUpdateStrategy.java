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
package org.springframework.cloud.kubernetes.config.reload;

import java.util.Objects;

/**
 * This is the superclass of all named strategies that can be fired when the configuration changes.
 */
public class ConfigurationUpdateStrategy {

    private String name;

    private Runnable reloadProcedure;

    public ConfigurationUpdateStrategy(String name, Runnable reloadProcedure) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(reloadProcedure, "reloadProcedure cannot be null");
        this.name = name;
        this.reloadProcedure = reloadProcedure;
    }

    public String getName() {
        return name;
    }

    public void reload() {
        this.reloadProcedure.run();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConfigurationUpdateStrategy{");
        sb.append("name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
