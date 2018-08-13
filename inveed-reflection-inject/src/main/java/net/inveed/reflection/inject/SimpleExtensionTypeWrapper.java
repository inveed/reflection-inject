package net.inveed.reflection.inject;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

public class SimpleExtensionTypeWrapper extends SimpleTypeWrapper {

	private SimpleTypeWrapper targetType;
	protected SimpleExtensionTypeWrapper(ClassPreProcessor injector, CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		super(injector, type, javaClass);
	}

	@Override
	protected void init() throws NotFoundException, ClassNotFoundException {
		super.init();
		
		if (this.getSupertype() instanceof SimpleExtensionTypeWrapper) {
			this.targetType = ((SimpleExtensionTypeWrapper) this.getSupertype()).getTargetType();
		} else if (this.getSupertype() instanceof SimpleTypeWrapper){
			this.targetType = (SimpleTypeWrapper) this.getSupertype();
		} else {
			//TODO: log! 
		}
	}
	
	@Override
	public void extend(SimpleExtensionTypeWrapper ext) throws CannotCompileException, NotFoundException {
		this.getTargetType().extend(ext);
	}
	
	public SimpleTypeWrapper getTargetType() {
		return this.targetType;
	}
	
	@Override
	public Class<?> toClass() throws CannotCompileException {
		return null;
	}
}
