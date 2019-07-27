package org.checkerframework.checker.prototype;

import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.prototype.qual.CommonSigned;
import org.checkerframework.checker.prototype.qual.Signed;
import org.checkerframework.checker.prototype.qual.UnknownSigned;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationBuilder;

/** @checker_framework.manual #prototype-checker Prototype Checker */
public class PrototypeAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** The @CommonSigned annotation. */
    private final AnnotationMirror COMMON =
            AnnotationBuilder.fromClass(elements, CommonSigned.class);
    /** The @Signed annotation. */
    private final AnnotationMirror SIGNED = AnnotationBuilder.fromClass(elements, Signed.class);
    /** The @UnknownSigned annotation. */
    private final AnnotationMirror UNKNOWN_SIGNED =
            AnnotationBuilder.fromClass(elements, UnknownSigned.class);

    public PrototypeAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> result = getBundledTypeQualifiersWithoutPolyAll();
        return result;
    }

    @Override
    protected void addComputedTypeAnnotations(
            Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
        addUnknownSignedToSomeLocals(tree, type);
        super.addComputedTypeAnnotations(tree, type, iUseFlow);
    }

    private void addUnknownSignedToSomeLocals(Tree tree, AnnotatedTypeMirror type) {
        switch (type.getKind()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
                QualifierDefaults defaults = new QualifierDefaults(elements, this);
                defaults.addCheckedCodeDefault(UNKNOWN_SIGNED, TypeUseLocation.LOCAL_VARIABLE);
                defaults.annotate(tree, type);
                break;
            default:
        }
    }
}
