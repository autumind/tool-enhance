/*
 * Copyright (c) 2014 - 2017. MOBCB Technology Co.,Ltd. All rights Reserved.
 */

package com.iylyi.tools.spring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EnBeanUtils: Enhance {@link BeanUtils}.<br/>
 *
 * @author autumind
 * @version 1.00
 * @since 2017-09-14
 */
public class EnBeanUtils extends BeanUtils {

    /**
     * Log
     */
    private final static Log LOGGER = LogFactory.getLog(EnBeanUtils.class);

    /**
     * Object attribute copy.<br/>
     * <p>
     * When target object has not simple field (determined by {@link BeanUtils#isSimpleProperty(Class)}) in declared fields,
     * in order to ensure field generic type to be corresponding, additional list copy is done here.
     *
     * @param source      Source object
     * @param targetClass Target object type
     * @return Target object
     */
    public static <T> T copyProperties(Object source, Class<T> targetClass) {
        T target; // target object
        if (source == null) {
            target = null;
        } else {

            Field[] declaredFields = targetClass.getDeclaredFields();
            List<Field> ignoreFields = Arrays.stream(declaredFields)
                    .filter(declaredField -> !isSimpleProperty(declaredField.getType()))
                    .collect(Collectors.toList()); // ignore fields

            try {
                target = targetClass.newInstance();
                copyProperties(source, target, ignoreFields.stream().map(Field::getName).toArray(String[]::new));
                if (!ignoreFields.isEmpty()) {
                    // copy attribute which type is list.
                    for (Field field : ignoreFields) {
                        PropertyDescriptor sourcePd = getPropertyDescriptor(source.getClass(), field.getName());
                        if (sourcePd == null) {
                            continue;
                        }

                        Method readMethod = sourcePd.getReadMethod(); // source field read method.
                        if (readMethod == null) {
                            continue;
                        }

                        PropertyDescriptor targetPd = getPropertyDescriptor(targetClass, field.getName());
                        if (targetPd == null) {
                            continue;
                        }

                        Method writeMethod = targetPd.getWriteMethod(); // target field write method.
                        if (writeMethod == null) {
                            continue;
                        }

                        if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                            readMethod.setAccessible(true);
                        }

                        Object value = readMethod.invoke(source);
                        if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                            writeMethod.setAccessible(true);
                        }

                        if (value instanceof List) {
                            writeMethod.invoke(target,
                                    copyList(
                                            (List<?>) value,
                                            (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]));
                        } else if (value instanceof Set) {
                            writeMethod.invoke(target,
                                    copySet(
                                            (Set<?>) value,
                                            (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]));

                        } else if (value instanceof Map) {
                            try {
                                writeMethod.invoke(target,
                                        copyMap(
                                                (Map<?, ?>) value,
                                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0],
                                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1]));
                            } catch (ClassCastException e) {
                                LOGGER.warn("Cast simple value type failure...");
                            }
                        }
                    }
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                LOGGER.error("Object value deep copy failure...", e);
                target = null;
            }
        }
        return target;
    }

    /**
     * List copy.
     *
     * @param source             source list
     * @param targetElementClass target list element type
     * @return target list
     */
    public static <T> List<T> copyList(List<?> source, Class<T> targetElementClass) {
        return Optional.ofNullable(source)
                .orElse(Collections.emptyList())
                .stream()
                .map(item -> copyProperties(item, targetElementClass))
                .collect(Collectors.toList());
    }

    /**
     * Set copy.
     *
     * @param source             source set
     * @param targetElementClass target set element type
     * @return target set
     */
    public static <T> Set<T> copySet(Set<?> source, Class<T> targetElementClass) {
        return Optional.ofNullable(source)
                .orElse(Collections.emptySet())
                .stream()
                .map(item -> copyProperties(item, targetElementClass))
                .collect(Collectors.toSet());
    }

    /**
     * Map copy.
     *
     * @param source           source map
     * @param targetKeyClass   target map key type
     * @param targetValueClass target map value type
     * @return target map
     *
     * @throws ClassCastException See {@link ClassCastException}.
     */
    public static <K, V> Map<K, V> copyMap(
            Map<?, ?> source, Class<K> targetKeyClass, Class<V> targetValueClass) throws ClassCastException {
        return Optional.ofNullable(source)
                .orElse(Collections.emptyMap())
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                entry -> {
                                    Object key = entry.getKey();
                                    if (isSimpleProperty(targetKeyClass)) {
                                        return targetKeyClass.cast(key);
                                    }

                                    return copyProperties(key, targetKeyClass);
                                },
                                entry -> {
                                    Object value = entry.getValue();
                                    if (isSimpleProperty(targetValueClass)) {
                                        return targetValueClass.cast(value);
                                    }

                                    return copyProperties(value, targetValueClass);
                                }));
    }

}
