package org.springframework.cloud.kubernetes.ribbon;


import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;

final class Types {

    private Types() {
        //Utlity
    }

    static Class rawType(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        } else if (type instanceof TypeVariable) {
            return rawType(firstOrObject(((TypeVariable) type).getBounds()));
        } else if (type instanceof WildcardType) {
            return rawType(firstOrObject(((WildcardType) type).getUpperBounds()));
        } else if (type instanceof GenericArrayType) {
            return rawType(((GenericArrayType) type).getGenericComponentType());
        }
        return Object.class;
    }

    private static Type firstOrObject(Type[] types) {
        if (types.length > 0) {
            return rawType(types[0]);
        } else {
            return Void.class;
        }
    }
}
