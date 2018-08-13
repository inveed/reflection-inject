package net.inveed.reflection.inject;

import javax.persistence.Entity;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import net.inveed.reflection.inject.annotations.EntityExtension;
import net.inveed.reflection.inject.annotations.EntityExtensionPkColumn;

public class EntityExtensionTypeWrapper extends EntityTypeWrapper {
	
	private EntityExtension annotation;
	private EntityTypeWrapper targetType;
	
	protected EntityExtensionTypeWrapper(ClassPreProcessor injector, CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		super(injector, type, javaClass);
	}

	@Override
	public void prepare() {
	}
	
	protected void init() throws NotFoundException, ClassNotFoundException {
		super.init();
		
		this.annotation = (EntityExtension) this.getType().getAnnotation(EntityExtension.class);
			
		if (!this.getType().getSuperclass().hasAnnotation(Entity.class)) {
			//TODO: log! расширение должно наследоваться от расширяемого Entity!
		}

		if ((this.getType().getSuperclass().getModifiers() & ~Modifier.ABSTRACT) != 0) {
			//TODO: log! расширение должно наследоваться от не-абстрактного Entity!
		}
		
		
		if (this.getSupertype() instanceof EntityExtensionTypeWrapper) {
			this.targetType = ((EntityExtensionTypeWrapper) this.getSupertype()).getTargetType();
		} else if (this.getSupertype() instanceof EntityTypeWrapper){
			this.targetType = (EntityTypeWrapper) this.getSupertype();
		} else {
			//TODO: log! 
		}
		
		if (this.getTargetType().getIdFields().size() != this.getExtensionAnnotation().pkColumns().length) {
			//TODO: error!
		}
		if (this.getExtensionAnnotation().pkColumns().length > 1) {
			for (EntityExtensionPkColumn pk : this.getExtensionAnnotation().pkColumns()) {
				if (pk.referencedColumnName().length() == 0) {
					//TODO: error!
				}
			}
		}
	}
	
	public EntityExtension getExtensionAnnotation() {
		return this.annotation;
	}
	
	public EntityTypeWrapper getTargetType() {
		return this.targetType;
	}
	
	@Override
	public Class<?> toClass() throws CannotCompileException {
		return null;
	}
}
