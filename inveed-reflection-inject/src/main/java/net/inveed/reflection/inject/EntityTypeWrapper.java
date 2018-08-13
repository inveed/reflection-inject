package net.inveed.reflection.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;
import javax.persistence.SecondaryTables;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalIdCache;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.inveed.reflection.inject.annotations.EntityExtensionPkColumn;

public class EntityTypeWrapper extends TypeWrapper {
	private boolean isDirty = false;
	
	protected EntityTypeWrapper(ClassPreProcessor injector, CtClass type, Class<?> javaClass) throws NotFoundException, ClassNotFoundException {
		super(injector, type, javaClass);
	}

	@Override
	protected void init() throws NotFoundException, ClassNotFoundException {
		super.init();
		this.idFields = this.findIdFields();
		
	}
	private List<CtField> idFields;
	
	private List<CtField> findIdFields() {
		ArrayList<CtField> ret = new ArrayList<>();
		for (CtField fld : this.getType().getFields()) {
			if (!fld.hasAnnotation(Id.class))
				continue;
			ret.add(fld);
		}
		return ret;
	}
	
	public List<CtField> getIdFields() {
		return this.idFields;
	}
	
	@Override
	public void prepare() throws ClassNotFoundException, NotFoundException, CannotCompileException {
		if (Modifier.isFinal(this.getType().getModifiers())) {
			this.getType().setModifiers(Modifier.clear(this.getType().getModifiers(), Modifier.FINAL));
		}
		
		boolean ctrOk = false;
		for (CtConstructor ctr : this.getType().getDeclaredConstructors()) {
			if (ctr.getDeclaringClass().getName() != this.getType().getName())
				continue;
			if (ctr.getParameterTypes().length == 0) {
				if (Modifier.isPrivate(ctr.getModifiers()) || Modifier.isPackage(ctr.getModifiers())) {
					ctr.setModifiers(Modifier.setProtected(ctr.getModifiers()));
					ctrOk = true;
					//this.getType().removeConstructor(ctr);
					//this.getType().addConstructor(ctr);
					break;
				} else if (Modifier.isProtected(ctr.getModifiers()) || Modifier.isPublic(ctr.getModifiers())) {
					ctrOk = true;
					break;
				}
			}
		}
		if (!ctrOk) {
			CtConstructor ctr = CtNewConstructor.defaultConstructor(this.getType());
			ctr.setModifiers(Modifier.setProtected(ctr.getModifiers()));
			this.getType().addConstructor(ctr);
		}
		ConstPool constpool = this.getType().getClassFile().getConstPool();
		
		AnnotationsAttribute aattr = (AnnotationsAttribute) this.getType().getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
		boolean adirty = false;
		boolean cacheable = false;
		ArrayList<Annotation> al = new ArrayList<>();
		for (Annotation col : aattr.getAnnotations()) {
			al.add(col);
		}
		
		if (!this.getType().hasAnnotation(Cacheable.class)) {
			Annotation cacheableAnnotation = new Annotation(Cacheable.class.getName(), constpool);
			cacheableAnnotation.addMemberValue("value", new BooleanMemberValue(true, constpool));
			al.add(cacheableAnnotation);
			adirty = true;
			cacheable = true;
		} else {
			Cacheable c;
			c = (Cacheable) this.getType().getAnnotation(Cacheable.class);
			cacheable = c.value();			
		}
		
		if (this.isNaturalIdCached() && !this.getType().hasAnnotation(NaturalIdCache.class) && cacheable) {
			Annotation naidCacheableAnnotation = new Annotation(NaturalIdCache.class.getName(), constpool);
			al.add(naidCacheableAnnotation);
			adirty = true;
		}
		
		if (!this.getType().hasAnnotation(Cache.class)) {
			Annotation cacheableAnnotation = new Annotation(Cache.class.getName(), constpool);
			EnumMemberValue usgValue = new EnumMemberValue(constpool);
			usgValue.setType(CacheConcurrencyStrategy.class.getName());
			usgValue.setValue(CacheConcurrencyStrategy.READ_WRITE.name());
			
			cacheableAnnotation.addMemberValue("usage", usgValue);
			al.add(cacheableAnnotation);
			adirty = true;
		}
		if (adirty) {
			aattr.setAnnotations(al.toArray(new Annotation[0]));
			this.isDirty = true;
		}
		
		for (CtField fld : this.getType().getDeclaredFields()) {
			this.prepareField(fld, constpool, cacheable);
		}
	}
	
	private void prepareField(CtField fld, ConstPool constpool, boolean cacheable) throws NotFoundException, CannotCompileException {
		boolean adirty = false;
		AnnotationsAttribute aattr = (AnnotationsAttribute) fld.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
		if (aattr == null) {
			return;
		}
		ArrayList<Annotation> al = new ArrayList<>();
		for (Annotation col : aattr.getAnnotations()) {
			al.add(col);
		}
		
		if (cacheable) {
			if (fld.hasAnnotation(OneToMany.class) || fld.hasAnnotation(ManyToMany.class)) {
				if (!fld.hasAnnotation(Cache.class)) {
					Annotation cacheAnnotation = new Annotation(Cache.class.getName(), constpool);
					EnumMemberValue usgValue = new EnumMemberValue(constpool);
					usgValue.setType(CacheConcurrencyStrategy.class.getName());
					usgValue.setValue(CacheConcurrencyStrategy.READ_WRITE.name());
					
					cacheAnnotation.addMemberValue("usage", usgValue);
					al.add(cacheAnnotation);
					adirty = true;
				}
				
			}
		}
		if (adirty) {
			aattr.setAnnotations(al.toArray(new Annotation[0]));
			this.isDirty = true;
			this.getType().removeField(fld);
			this.getType().addField(fld);
		}
	}
	
	private boolean modifyColumnDefinition(AnnotationsAttribute oaattr, AnnotationsAttribute naattr, EntityExtensionTypeWrapper ext, ConstPool constpool) {
		boolean ret = false;
		ArrayList<Annotation> al = new ArrayList<>();
		for (Annotation col : oaattr.getAnnotations()) {
			Annotation na = new Annotation(col.getTypeName(), constpool);
			
			if (col.getMemberNames() != null) {
				col.getMemberNames().forEach(new Consumer<String>() {

					@Override
					public void accept(String name) {
						MemberValue v = col.getMemberValue(name);
						na.addMemberValue(name, v);
					}
					
				});
			}
			
			if (col.getTypeName().equals(JoinColumn.class.getName()) || col.getTypeName().equals(Column.class.getName())) {
				this.setTableName(na, ext, constpool);
				
				ret = true;
			} else if (col.getTypeName().equals(ManyToMany.class.getName())) {
				//TODO: many-to-many
			}
			al.add(na);
		}
		naattr.setAnnotations(al.toArray(new Annotation[0]));
		return ret;
	}
	
	private void setTableName(Annotation col, EntityExtensionTypeWrapper ext, ConstPool constpool) {
		MemberValue mv = col.getMemberValue("table");
		if (mv == null) {
			mv = new StringMemberValue(constpool);
			col.addMemberValue("table", mv);
		}
		if (mv instanceof StringMemberValue) {
			StringMemberValue sv = (StringMemberValue) mv;
			sv.setValue(ext.getExtensionAnnotation().tableName());
		}
	}
	
	public void extend(EntityExtensionTypeWrapper ext) throws CannotCompileException, NotFoundException {
		this.isDirty = true;
		ConstPool constpool = this.getType().getClassFile().getConstPool();

		for (CtField fld : ext.getType().getDeclaredFields()) {
			if (Modifier.isStatic(fld.getModifiers())) {
				continue;
			}
			CtField nfld = new CtField(fld, this.getType());
			if (!Modifier.isTransient(nfld.getModifiers())) {
				AnnotationsAttribute oaattr = (AnnotationsAttribute) fld.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
				AnnotationsAttribute aattr = (AnnotationsAttribute) nfld.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
				if (aattr != null)
					if (!modifyColumnDefinition(oaattr, aattr, ext, constpool)) {
						//TODO: Error! поле без определения колонок!
					}
			}
			this.getType().addField(nfld);
		}
		for (CtMethod m : ext.getType().getDeclaredMethods()) {
			if (Modifier.isStatic(m.getModifiers()))
				continue;
			
			CtMethod nm = new CtMethod(m, this.getType(), null);
			AnnotationsAttribute oaattr = (AnnotationsAttribute) m.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
			AnnotationsAttribute naattr = (AnnotationsAttribute) nm.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
			if (oaattr != null) {
				if (naattr == null) {
					naattr = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
					nm.getMethodInfo().addAttribute(naattr);
				}
				this.modifyColumnDefinition(oaattr, naattr, ext, constpool);
			}
				
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
		
		// Adding annotations
		AnnotationsAttribute attrAnnotation = (AnnotationsAttribute) this.getType().getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
		
		
		String secTableName = ext.getExtensionAnnotation().tableName().trim();
		if (secTableName.length() > 0) {
			
			Annotation secondaryTablesTypeAnnotation = attrAnnotation.getAnnotation(SecondaryTables.class.getName());
			if (secondaryTablesTypeAnnotation == null) {
				secondaryTablesTypeAnnotation = new Annotation(SecondaryTables.class.getName(), constpool);
				ArrayMemberValue v = new ArrayMemberValue(constpool);
				v.setValue(new MemberValue[0]);
				secondaryTablesTypeAnnotation.addMemberValue("value", v);
				attrAnnotation.addAnnotation(secondaryTablesTypeAnnotation);
			}
			
			// SecondaryTable annotation
			Annotation secTable = new Annotation(SecondaryTable.class.getName(), constpool);
			
			secTable.addMemberValue("name", new StringMemberValue(secTableName, constpool));
			
			ArrayMemberValue pks = new ArrayMemberValue(constpool);
			
			ArrayList<MemberValue> mvlist = new ArrayList<>();
			for (EntityExtensionPkColumn clmn : ext.getExtensionAnnotation().pkColumns()) {
				Annotation pkAnnotation = new Annotation(PrimaryKeyJoinColumn.class.getName(), constpool);
				pkAnnotation.addMemberValue("name", new StringMemberValue(clmn.name(), constpool));
				pkAnnotation.addMemberValue("referencedColumnName", new StringMemberValue(clmn.referencedColumnName(), constpool));
				pkAnnotation.addMemberValue("columnDefinition", new StringMemberValue(clmn.columnDefinition(), constpool));
				
				AnnotationMemberValue mv = new AnnotationMemberValue(pkAnnotation, constpool);
				mvlist.add(mv);
			}
			pks.setValue(mvlist.toArray(new MemberValue[0]));
			
			secTable.addMemberValue("pkJoinColumns", pks);
			
			ArrayMemberValue stables = (ArrayMemberValue) secondaryTablesTypeAnnotation.getMemberValue("value");
			ArrayList<MemberValue> stablesList = new ArrayList<>();
			for (MemberValue mv : stables.getValue()) {
				stablesList.add(mv);
			}
			
			stablesList.add(new AnnotationMemberValue(secTable, constpool));
			stables.setValue(stablesList.toArray(new MemberValue[0]));
			attrAnnotation.addAnnotation(secondaryTablesTypeAnnotation);
			//this.getType().getClassFile().addAttribute(attrAnnotation);
		}
	}

	@Override
	public boolean isDirty() {
		return this.isDirty;
	}
}
