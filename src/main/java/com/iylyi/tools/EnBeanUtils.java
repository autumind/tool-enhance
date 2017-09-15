/*
 * Copyright (c) 2017. WWW.IYLYI.COM. All rights Reserved.
 */

package com.iylyi.tools;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            List<Field> ignoreFields = new ArrayList<>(); // ignore fields
            for (Field field : declaredFields) {
                Class<?> type = field.getType();
                if (!BeanUtils.isSimpleProperty(type)) {
                    ignoreFields.add(field);
                }
            }

            try {
                target = targetClass.newInstance();
                BeanUtils.copyProperties(source, target, ignoreFields.stream().map(Field::getName).toArray(String[]::new));
                if (!ignoreFields.isEmpty()) {
                    // copy attribute which type is list.
                    for (Field field : ignoreFields) {
                        PropertyDescriptor sourcePd = BeanUtils.getPropertyDescriptor(source.getClass(), field.getName());
                        Method readMethod = sourcePd.getReadMethod(); // source field read method.

                        PropertyDescriptor targetPd = BeanUtils.getPropertyDescriptor(targetClass, field.getName());
                        Method writeMethod = targetPd.getWriteMethod(); // target field write method.

                        if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                            readMethod.setAccessible(true);
                        }

                        Object value = readMethod.invoke(source);
                        if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                            writeMethod.setAccessible(true);
                        }

                        writeMethod.invoke(target,
                                copyList(
                                        (List<?>) value,
                                        (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]));
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
        return Optional.of(source)
                .orElse(null)
                .stream()
                .map(item -> copyProperties(item, targetElementClass))
                .collect(Collectors.toList());
    }
}
