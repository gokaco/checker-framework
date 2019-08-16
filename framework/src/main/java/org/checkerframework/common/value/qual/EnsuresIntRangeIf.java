package org.checkerframework.common.value.qual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.ConditionalPostconditionAnnotation;
import org.checkerframework.framework.qual.InheritedAnnotation;
import org.checkerframework.framework.qual.QualifierArgument;

@ConditionalPostconditionAnnotation(qualifier = IntRange.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@InheritedAnnotation
public @interface EnsuresIntRangeIf {
    /**
     * Java expression(s) that are a sequence with the given range after the method returns the
     * given result.
     *
     * @checker_framework.manual #java-expressions-as-arguments Syntax of Java expressions
     */
    String[] expression();

    /** The return value of the method that needs to hold for the postcondition to hold. */
    boolean result();

    /** Smallest value in the range, inclusive. */
    @QualifierArgument("from")
    long targetFrom() default Long.MIN_VALUE;

    /** Largest value in the range, inclusive. */
    @QualifierArgument("to")
    long targetTo() default Long.MAX_VALUE;
}
