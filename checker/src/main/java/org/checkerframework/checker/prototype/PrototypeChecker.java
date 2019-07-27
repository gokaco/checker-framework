package org.checkerframework.checker.prototype;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.RelevantJavaTypes;

@RelevantJavaTypes({Byte.class, Short.class, Integer.class, Long.class, Character.class})
public class PrototypeChecker extends BaseTypeChecker {}
