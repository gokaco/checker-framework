package org.checkerframework.checker.signedness;

import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.index.IndexUtil;
import org.checkerframework.checker.signedness.qual.Signed;
import org.checkerframework.checker.signedness.qual.SignedPositive;
import org.checkerframework.checker.signedness.qual.SignednessCommon;
import org.checkerframework.checker.signedness.qual.SignednessGlb;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.value.ValueAnnotatedTypeFactory;
import org.checkerframework.common.value.ValueChecker;
import org.checkerframework.common.value.qual.IntRangeFromNonNegative;
import org.checkerframework.common.value.qual.IntRangeFromPositive;
import org.checkerframework.common.value.util.Range;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationBuilder;

/** @checker_framework.manual #signedness-checker Signedness Checker */
public class SignednessAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** The @SignednessGlb annotation. */
    private final AnnotationMirror SIGNEDNESS_GLB =
            AnnotationBuilder.fromClass(elements, SignednessGlb.class);
    /** The @SignednessCommon annotation. */
    private final AnnotationMirror SIGNEDNESS_COMMON =
            AnnotationBuilder.fromClass(elements, SignednessCommon.class);
    /** The @Signed annotation. */
    private final AnnotationMirror SIGNED = AnnotationBuilder.fromClass(elements, Signed.class);

    /** The @NonNegative annotation of the Index Checker, as represented by the Value Checker. */
    private final AnnotationMirror INT_RANGE_FROM_NON_NEGATIVE =
            AnnotationBuilder.fromClass(elements, IntRangeFromNonNegative.class);
    /** The @Positive annotation of the Index Checker, as represented by the Value Checker. */
    private final AnnotationMirror INT_RANGE_FROM_POSITIVE =
            AnnotationBuilder.fromClass(elements, IntRangeFromPositive.class);

    ValueAnnotatedTypeFactory valueFactory = getTypeFactoryOfSubchecker(ValueChecker.class);

    /** Create a SignednessAnnotatedTypeFactory. */
    public SignednessAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);

        addAliasedAnnotation(SignedPositive.class, SIGNEDNESS_GLB);

        postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> result = getBundledTypeQualifiersWithoutPolyAll();
        result.remove(SignedPositive.class); // this method should not return aliases
        return result;
    }

    // Refines the type of an integer primitive to @SignednessCommon if it is within the signed
    // positive range (i.e. its MSB is zero).
    @Override
    protected void addComputedTypeAnnotations(
            Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
        super.addComputedTypeAnnotations(tree, type, iUseFlow);

        switch (type.getKind()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
                AnnotatedTypeMirror valueATM = valueFactory.getAnnotatedType(tree);
                if ((valueATM.hasAnnotation(INT_RANGE_FROM_NON_NEGATIVE)
                                || valueATM.hasAnnotation(INT_RANGE_FROM_POSITIVE))
                        && type.hasAnnotation(SIGNED)) {
                    type.replaceAnnotation(SIGNEDNESS_COMMON);
                } else {
                    Range treeRange = IndexUtil.getPossibleValues(valueATM, valueFactory);
                    if (treeRange != null) {
                        switch (type.getKind()) {
                            case BYTE:
                                if (treeRange.isWithin(0, Byte.MAX_VALUE)) {
                                    type.replaceAnnotation(SIGNEDNESS_COMMON);
                                }
                                break;
                            case SHORT:
                                if (treeRange.isWithin(0, Short.MAX_VALUE)) {
                                    type.replaceAnnotation(SIGNEDNESS_COMMON);
                                }
                                break;
                            case INT:
                                if (treeRange.isWithin(0, Integer.MAX_VALUE)) {
                                    type.replaceAnnotation(SIGNEDNESS_COMMON);
                                }
                                break;
                            case LONG:
                                if (treeRange.isWithin(0, Long.MAX_VALUE)) {
                                    type.replaceAnnotation(SIGNEDNESS_COMMON);
                                }
                                break;
                            default:
                                // Nothing
                        }
                    }
                }
                break;
            default:
                // Nothing
        }
    }
}
