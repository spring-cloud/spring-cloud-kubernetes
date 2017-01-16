package org.springframework.cloud.kubernetes.ribbon;


import org.junit.Assert;
import org.junit.Test;

public class KubernetesConfigKeyTest {

    private class TypeOne<T> {}

    @Test
    public <T extends TypeOne, I> void testTypes() {
        //with class
        KubernetesConfigKey<String> key1 = new KubernetesConfigKey<String>("key1"){};

        //with type variable
        KubernetesConfigKey<T> key2 = new KubernetesConfigKey<T>("key2"){};

        //with type variable with no bounds
        KubernetesConfigKey<I> key3 = new KubernetesConfigKey<I>("key3"){};

        Assert.assertEquals(String.class, key1.type());
        Assert.assertEquals(TypeOne.class, key2.type());
        Assert.assertEquals(Object.class, key3.type());
    }

}