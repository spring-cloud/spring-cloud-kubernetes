package io.fabric8.spring.cloud.kubernetes.reload;

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
