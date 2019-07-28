package org.checkerframework.checker.prototype.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeKind;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf(UnknownSigned.class)
@DefaultFor(typeKinds = {TypeKind.BYTE, TypeKind.INT, TypeKind.LONG, TypeKind.SHORT, TypeKind.CHAR})
public @interface Signed {}
