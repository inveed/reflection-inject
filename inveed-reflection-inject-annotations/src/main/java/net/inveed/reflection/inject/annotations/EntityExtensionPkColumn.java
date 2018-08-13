package net.inveed.reflection.inject.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a primary key column for multy-table extension with {@link net.inveed.reflection.inject.annotations.EntityExtension}
 *
 */
@Target(value={ElementType.TYPE,ElementType.METHOD,ElementType.FIELD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface EntityExtensionPkColumn {
	String name();
	String referencedColumnName() default "";
	String columnDefinition() default "";
}
