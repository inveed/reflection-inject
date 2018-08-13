package net.inveed.reflection.inject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.persistence.Entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import net.inveed.reflection.inject.annotations.EntityExtension;
import net.inveed.reflection.inject.annotations.SimpleExtension;

public class ClassPreProcessor {
	private static final Logger LOG = LoggerFactory.getLogger(ClassPreProcessor.class);
	
	private ClassPool pool;
	private final HashMap<String, TypeWrapper> registeredTypes = new HashMap<>();
	private final ArrayList<TypeWrapper> interestingTypes = new ArrayList<>();
	private final ArrayList<Class<?>> interestingAnnotations = new ArrayList<>();
	
	private HashMap<Class<?>, List<Class<?>>> interestingTypesMap;
	private HashMap<Class<? extends Annotation>, List<Class<?>>> interestingAnnotationsMap;
	
	final HashMap<String, TypeWrapper> wrappers = new HashMap<>();
	
	public ClassPreProcessor() {
		this.pool = ClassPool.getDefault();
		this.pool.insertClassPath(new ClassClassPath(ClassPreProcessor.class));
	}
	
	public void registerInterest(Class<?> type) {
		if (Annotation.class.isAssignableFrom(type)) {
			if (this.interestingAnnotations.contains(type))
				return;
			this.interestingAnnotations.add(type);
		} else {
			// Is TypeWrapper for requested class already registered - exiting.
			for (TypeWrapper rtw : this.interestingTypes) {
				if (rtw.getType().getName().equals(type.getName())) {
					return;
				}
			}

			TypeWrapper tw;
			try {
				tw = this.wrap(type);
			} catch (ClassNotFoundException | NotFoundException e) {
				return;
			}
			this.interestingTypes.add(tw);
		}
	}
	
	public void buildClasses() throws CannotCompileException, NotFoundException, ClassNotFoundException {
		for (TypeWrapper tw : this.registeredTypes.values()) {
			if (tw instanceof SimpleExtensionTypeWrapper) {
				SimpleExtensionTypeWrapper ext = (SimpleExtensionTypeWrapper) tw;
				try {
					ext.getTargetType().extend(ext);
				} catch (CannotCompileException | NotFoundException e) {
					LOG.error("Cannot extend class '" + ext.getTargetType().getType().getName() +"' with extention '" + ext.getType().getName() + "'" , e);
					throw e;
				}
			}
			else if (tw instanceof EntityExtensionTypeWrapper) {
				EntityExtensionTypeWrapper ext = (EntityExtensionTypeWrapper) tw;
				try {
					ext.getTargetType().extend(ext);
				} catch (CannotCompileException | NotFoundException e) {
					LOG.error("Cannot extend class '" + ext.getTargetType().getType().getName() +"' with extention '" + ext.getType().getName() + "'" , e);
					throw e;
				}
			}
		}
		
		for (TypeWrapper tw : this.registeredTypes.values()) {
			try {
				tw.prepare();
			} catch (CannotCompileException | NotFoundException | ClassNotFoundException e) {
				LOG.error("Cannot prepare class '" + tw.getType().getName() +"'." , e);
				throw e;
			}
		}
		
		for (TypeWrapper tw : this.registeredTypes.values()) {
			try {
				tw.toClass();
			} catch (CannotCompileException e) {
				LOG.error("Cannot compile class '" + tw.getType().getName() +"'." , e);
				throw e;
			}
		}
		for (TypeWrapper tw : this.registeredTypes.values()) {
			if (tw instanceof SimpleExtensionTypeWrapper)
				continue;
			if (tw instanceof EntityExtensionTypeWrapper) {
				continue;
			}
			
			Class<?> cl = tw.toClass();
			if (!tw.isDirty())
				continue;
				
			LOG.debug("DUMPED TYPE: ");
			dumpClass(cl, "");
			LOG.debug("-------------------------------");
		}
	}
	
	private void dumpClass(Class<?> c, String o) {
		LOG.debug(o + "CLASS: " + c.getName());
		o = o + " ";
		for (java.lang.annotation.Annotation a : c.getAnnotations()) {
			LOG.debug(o + "  ANNOTATION: " + a.toString());
		}
		o = o + " ";
		for (Field f : c.getDeclaredFields()) {
			LOG.debug(o + " FIELD: " + f.getName() + " TYPE: " + f.getType());
			for (java.lang.annotation.Annotation a : f.getAnnotations()) {
				LOG.debug(o + "  ANNOTATION: " + a.toString());
			}
		}
		if (c.getSuperclass() != null) {
			if (c.getSuperclass() != Object.class) {
				dumpClass(c.getSuperclass(), o + " ");
			}
		}
	}	

	@SuppressWarnings("unchecked")
	public void buildInterests() throws CannotCompileException, ClassNotFoundException, NotFoundException {
		this.buildClasses();
		
		HashMap<Class<?>, List<Class<?>>> interestingTypesMap = new HashMap<>();
		for (TypeWrapper t : this.interestingTypes) {
			ArrayList<Class<?>> list = new ArrayList<>();
			interestingTypesMap.put(t.toClass(), list);
			
			for (TypeWrapper tw : this.wrappers.values()) {
				if (tw.isInherited(t) || tw.isImplemets(t)) {
					Class<?> tc = tw.toClass();
					list.add(tc);
				}
			}
		}
		HashMap<Class<? extends Annotation>, List<Class<?>>> interestingAnnotationsMap = new HashMap<>();
		for (Class<?> t : this.interestingAnnotations) {
			ArrayList<Class<?>> list = new ArrayList<>();
			interestingAnnotationsMap.put((Class<? extends Annotation>) t, list);
			
			for (TypeWrapper tw : this.wrappers.values()) {
				if (tw.getType().hasAnnotation(t)) {
					Class<?> tc = tw.toClass();
					list.add(tc);
				}
			}
		}
		
		this.interestingAnnotationsMap = interestingAnnotationsMap;
		this.interestingTypesMap = interestingTypesMap;
	}
	
	public List<Class<?>> getInterest(Class <?> type) {
		if (Annotation.class.isAssignableFrom(type)) {
			return Collections.unmodifiableList(this.interestingAnnotationsMap.get(type));
		} else
			return Collections.unmodifiableList(this.interestingTypesMap.get(type));
	}
	
	public ClassPath insertClassPath(ClassPath cp) {
		return pool.insertClassPath(cp);
	}
	
	private TypeWrapper wrap(Class<?> type) throws NotFoundException, ClassNotFoundException {
		CtClass t = pool.get(type.getName());
		TypeWrapper tw = wrap(t, type);
		return tw;
	}
	
	public TypeWrapper wrap(String typeName) throws NotFoundException, ClassNotFoundException {
		CtClass type = pool.get(typeName);
		return wrap(type, null);
	}
	
	public TypeWrapper wrap(InputStream stream) throws NotFoundException, ClassNotFoundException, IOException, RuntimeException {
		CtClass type = pool.makeClass(stream);
		return wrap(type, null);
	}
	
	public TypeWrapper wrap(CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		if (wrappers.containsKey(type.getName())) {
			return wrappers.get(type.getName());
		}
		TypeWrapper tw;
		if (type.hasAnnotation(EntityExtension.class)) {
			tw = new EntityExtensionTypeWrapper(this, type, javaClass);
		} else if (type.hasAnnotation(Entity.class)) {
			tw = new EntityTypeWrapper(this, type, javaClass);
		} else if (type.hasAnnotation(SimpleExtension.class)) {
			tw = new SimpleExtensionTypeWrapper(this, type, javaClass);
		} else {
			tw = new SimpleTypeWrapper(this, type, javaClass);
		}
		return tw;
	}
	public void close() {
		for (TypeWrapper tw : this.registeredTypes.values()) {
			tw.getType().detach();
		}
		this.registeredTypes.clear();
		this.pool = null;
	}
	
	public void registerClass(InputStream stream) throws NotFoundException, ClassNotFoundException, IOException, RuntimeException {
		TypeWrapper tw = this.wrap(stream);

		while (tw != null) {
			if (registeredTypes.containsKey(tw.getType().getName()))
				return;
			registeredTypes.put(tw.getType().getName(), tw);
			tw = tw.getSupertype();
		}
	}
	
	public void registerClass(String classname) throws NotFoundException, ClassNotFoundException {
		TypeWrapper tw = this.wrap(classname);

		while (tw != null) {
			if (registeredTypes.containsKey(tw.getType().getName()))
				return;
			registeredTypes.put(tw.getType().getName(), tw);
			tw = tw.getSupertype();
		}
	}
}
