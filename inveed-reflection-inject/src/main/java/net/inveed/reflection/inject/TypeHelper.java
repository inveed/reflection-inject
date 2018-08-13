package net.inveed.reflection.inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeHelper {
	private static final Logger LOG = LoggerFactory.getLogger(TypeHelper.class);
	
	public static final <T> T createInstance(Class<T> type) {
		Constructor<T> ctr = getDefaultConstructor(type);
		if (ctr == null) {
			LOG.error("Cannot find default constructor for type " + type);
			return null;
		}
		boolean acc = ctr.isAccessible();
		ctr.setAccessible(true);
		try {
			return ctr.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			LOG.error("Cannot create instance of class " + type + " using default constructor", e);
		} finally {
			ctr.setAccessible(acc);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> Constructor<T> getDefaultConstructor(Class<T> type) {
		if (Modifier.isAbstract(type.getModifiers()))
			return null;

		if (Modifier.isInterface(type.getModifiers()))
			return null;

		Constructor<?>[] ca = type.getConstructors();
		for (Constructor<?> c : ca) {
			if (c.getParameterTypes().length == 0)
				return (Constructor<T>) c;
		}
		return null;
	}

	public static final Class<?> findLoadedClass(String classname) {
		try {
			Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
			m.setAccessible(true);
			ClassLoader cl = ClassLoader.getSystemClassLoader();
			Object ret = m.invoke(cl, classname);
			if (ret instanceof Class)
				return (Class<?>) ret;
			
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
		}
		return null;

	}
}
