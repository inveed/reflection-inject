package net.inveed.reflection.inject;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

public class SimpleTypeWrapper extends TypeWrapper{
	private boolean isDirty = false;
	
	protected SimpleTypeWrapper(ClassPreProcessor injector, CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		super(injector, type, javaClass);
	}
	
	public void extend(SimpleExtensionTypeWrapper ext) throws CannotCompileException, NotFoundException {
		this.isDirty = true;
		for (CtField fld : ext.getType().getDeclaredFields()) {
			if (Modifier.isStatic(fld.getModifiers())) {
				continue;
			}
			CtField nfld = new CtField(fld, this.getType());
			this.getType().addField(nfld);
		}
		for (CtMethod m : ext.getType().getDeclaredMethods()) {
			if (Modifier.isStatic(m.getModifiers()))
				continue;
			
			CtMethod nm = new CtMethod(m, this.getType(), null);
			this.getType().addMethod(nm);
		}
		
		for (CtClass i : ext.getType().getInterfaces()) {
			boolean inherited = false;
			for (CtClass di : this.getType().getInterfaces()) {
				if (di == i) {
					inherited = true;
					break;
				}
			}
			if (!inherited){
				this.getType().addInterface(i);
			}
		}
	}

	@Override
	public boolean isDirty() {
		return this.isDirty;
	}
}
