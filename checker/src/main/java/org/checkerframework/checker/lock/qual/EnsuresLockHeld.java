package org.checkerframework.checker.lock.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.InheritedAnnotation;
import org.checkerframework.framework.qual.PostconditionAnnotation;

/**
 * Indicates that the given expressions are held if the method terminates successfully.
 *
 * @see EnsuresLockHeldIf
 * @checker_framework.manual #lock-checker Lock Checker
 * @checker_framework.manual #ensureslockheld-examples Example use of @EnsuresLockHeld
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@PostconditionAnnotation(qualifier = LockHeld.class)
@InheritedAnnotation
@Repeatable(EnsuresLockHeld.List.class)
public @interface EnsuresLockHeld {
    /**
     * The Java expressions whose values are held after the method terminates successfully.
     *
     * @see <a href="https://checkerframework.org/manual/#java-expressions-as-arguments">Syntax of
     *     Java expressions</a>
     */
    String[] value();

    /**
     * Programmers generally do not need to write this; it is created by Java when a programmer
     * writes more than one {@link EnsuresLockHeld} annotation at the same location.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @PostconditionAnnotation(qualifier = LockHeld.class)
    @InheritedAnnotation
    @interface List {
        /** The array that contains all the repeatable annotations. */
        EnsuresLockHeld[] value();
    }
}
