package net.inveed.reflection.inject.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an extension of another JPA entity
 * 
 * Class will not be promoted to the JRE after processing, but extended entity will be modified such way:
 *  - extended class will get all fields and methods of "extension" class
 *  - inherits all interfaces of "extension" class
 *  - JPA annotations will be modified with "table" property to separate extension from "main" table in database
 *  
 * Access to extension methods and fields can be achieved with interfaces.
 *
 */
@Target(value=ElementType.TYPE)
@Retention(value=RetentionPolicy.RUNTIME)
public @interface EntityExtension {
	String tableName() default "";
	EntityExtensionPkColumn[] pkColumns() default {};
}
