package net.inveed.reflection.inject;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.NaturalIdCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

public abstract class TypeWrapper {
	private static final Logger LOG = LoggerFactory.getLogger(TypeWrapper.class);
	
	public void prepare() throws ClassNotFoundException, NotFoundException, CannotCompileException {
	}
	//
	
	private CtClass type;
	private TypeWrapper superType;
	private final List<TypeWrapper> subTypes = new ArrayList<>();
	
	private final List<TypeWrapper> interfaceTypes = new ArrayList<>();
	
	private Class<?> javaClass;
	
	protected TypeWrapper(ClassPreProcessor injector, CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		this.type = type;
		this.javaClass = javaClass;
		injector.wrappers.put(type.getName(), this);
	
		try {
			if (!type.getSuperclass().getName().equals(Object.class.getName())) {
				Class<?> superJavaClass = null;
				if (this.isLoaded())
					superJavaClass = Class.forName(type.getSuperclass().getName());
					
				this.superType = injector.wrap(type.getSuperclass(), superJavaClass);
				
				this.superType.subTypes.add(this);
			}
			
			
			for (CtClass itype : this.getType().getInterfaces()) {
				Class<?> superJavaClass = null;
				if (this.isLoaded())
					superJavaClass = Class.forName(itype.getName());
				
				
				TypeWrapper itypeWrapper = injector.wrap(itype, superJavaClass);
				interfaceTypes.add(itypeWrapper);
			}
			
			if (this.superType != null) {
				for (TypeWrapper pit : this.superType.interfaceTypes) {
					if (!this.interfaceTypes.contains(pit)) {
						this.interfaceTypes.add(pit);
					}
				}
			}
			
			ArrayList<TypeWrapper> inheritedInterfaces = new ArrayList<>();
			for (TypeWrapper it : this.interfaceTypes) {
				for (TypeWrapper pit : it.interfaceTypes) {
					if (!this.interfaceTypes.contains(pit) && !inheritedInterfaces.contains(pit)) {
						inheritedInterfaces.add(pit);
					}
				}
			}
			
			this.interfaceTypes.addAll(inheritedInterfaces);
			
		} catch (NotFoundException e) {
			return;
		} finally {
			this.init();
		}
	}
	
	protected void init() throws NotFoundException, ClassNotFoundException {
	}
	
	public CtClass getType() {
		return this.type;
	}
	
	public TypeWrapper getSupertype() {
		return this.superType;
	}

	public boolean isInherited(TypeWrapper type) {
		if (this.superType == null)
			return false;
		if (this.superType == type) 
			return true;
		return this.superType.isInherited(type);
	}
	
	public boolean isImplemets(TypeWrapper itype) {
		return this.interfaceTypes.contains(itype);
	}
	
	
	public boolean isNaturalIdCached() {
		if (this.getType().hasAnnotation(NaturalIdCache.class))
			return true;
		
		if (this.getSupertype() != null)
			return this.getSupertype().isNaturalIdCached();
		return false;
		
	}
	
	public Class<?> toClass() throws CannotCompileException {
		if (this.javaClass == null)
			this.javaClass = TypeHelper.findLoadedClass(this.getType().getName());
		
		if (this.javaClass != null)
			return this.javaClass;
		
		if (this.getType().getName().startsWith("java.")) {
			try {
				this.javaClass = Class.forName(this.getType().getName());
				return this.javaClass;
			} catch (ClassNotFoundException e) {
				LOG.warn("Cannot generate dynamic class", e);
			}
		}
	
		if (this.getSupertype() != null) {
			this.getSupertype().toClass();
		}
		for (TypeWrapper iw : this.interfaceTypes) {
			iw.toClass();
		}
		
		this.javaClass = this.getType().toClass();
		return this.javaClass;
	}
	
	public abstract boolean isDirty();
	
	public boolean isLoaded() {
		return this.javaClass != null;
	}
}
