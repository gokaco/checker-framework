package org.checkerframework.checker.signedness;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.signedness.qual.UnknownSignedness;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.value.ValueAnnotatedTypeFactory;
import org.checkerframework.common.value.ValueChecker;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationBuilder;

/** @checker_framework.manual #signedness-checker Signedness Checker */
public class SignednessAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    private final AnnotationMirror UNKNOWN_SIGNEDNESS;
    private final AnnotationMirror LITERAL;
    private final AnnotationMirror CONSTANT_POSITIVE;

    private ValueAnnotatedTypeFactory valueAtypefactory;

    /**
     * Provides a way to query the Constant Value Checker, which computes the values of expressions
     * known at compile time (constant propagation and folding).
     */
    private ValueAnnotatedTypeFactory getValueAnnotatedTypeFactory() {
        if (valueAtypefactory == null) {
            valueAtypefactory = getTypeFactoryOfSubchecker(ValueChecker.class);
        }
        return valueAtypefactory;
    }

    // These are commented out until issues with making boxed implicitly signed
    // are worked out. (https://github.com/typetools/checker-framework/issues/797)
    /*
    private final String JAVA_LANG_BYTE = "java.lang.Byte";
    private final String JAVA_LANG_SHORT = "java.lang.Short";
    private final String JAVA_LANG_INTEGER = "java.lang.Integer";
    private final String JAVA_LANG_LONG = "java.lang.Long";
    */

    public SignednessAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        UNKNOWN_SIGNEDNESS = AnnotationBuilder.fromClass(elements, UnknownSignedness.class);
        LITERAL = AnnotationBuilder.fromClass(elements, Literal.class);
        CONSTANT_POSITIVE = AnnotationBuilder.fromClass(elements, ConstantPositive.class);

        postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return getBundledTypeQualifiersWithoutPolyAll();
    }

    /** {@inheritDoc} */
    @Override
    protected void addComputedTypeAnnotations(
            Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
        // When it is possible to default types based on their TypeKinds,
        // this method will no longer be needed.
        // Currently, it is adding the LOCAL_VARIABLE default for
        // bytes, shorts, ints, and longs so that the implicit for
        // those types is not applied when they are local variables.
        // Only the local variable default is applied first because
        // it is the only refinable location (other than fields) that could
        // have a primitive type.

        addUnknownSignednessToIntegralLocals(tree, type);
        super.addComputedTypeAnnotations(tree, type, iUseFlow);
    }

    /**
     * If the tree is a local variable and the type is a byte, short, int or long, then adds the
     * UnknownSignedness annotation so that dataflow can refine it.
     */
    private void addUnknownSignednessToIntegralLocals(Tree tree, AnnotatedTypeMirror type) {
        switch (type.getKind()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case CHAR:
                QualifierDefaults defaults = new QualifierDefaults(elements, this);
                defaults.addCheckedCodeDefault(UNKNOWN_SIGNEDNESS, TypeUseLocation.LOCAL_VARIABLE);
                defaults.annotate(tree, type);
                break;
            default:
                // Nothing for other cases.
        }

        // This code is commented out until boxed primitives can be made implicitly signed.
        // (https://github.com/typetools/checker-framework/issues/797)

        /*switch (TypesUtils.getQualifiedName(type.getUnderlyingType()).toString()) {
        case JAVA_LANG_BYTE:
        case JAVA_LANG_SHORT:
        case JAVA_LANG_INTEGER:
        case JAVA_LANG_LONG:
            QualifierDefaults defaults = new QualifierDefaults(elements, this);
            defaults.addCheckedCodeDefault(UNKNOWN_SIGNEDNESS, TypeUseLocation.LOCAL_VARIABLE);
            defaults.annotate(tree, type);
        }*/

    }

    /** {@inheritDoc} */
    @Override
    protected TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(
                new SignednessTreeAnnotator(this), super.createTreeAnnotator());
    }

    /**
     * This TreeAnnotator ensures that boolean expressions are not given Unsigned or Signed
     * annotations by {@link PropagationTreeAnnotator}, that shift results take on the type of their
     * left operand, and that the types of identifiers are refined based on the results of the Value
     * Checker.
     */
    // TODO: Refine the type of expressions using the Value Checker as well.
    private class SignednessTreeAnnotator extends TreeAnnotator {

        public SignednessTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        /**
         * Change the type of booleans to {@code @UnknownSignedness} so that the {@link
         * PropagationTreeAnnotator} does not change the type of them.
         */
        private void annotateBoolean(AnnotatedTypeMirror type) {
            switch (type.getKind()) {
                case BOOLEAN:
                    type.addAnnotation(UNKNOWN_SIGNEDNESS);
                    break;
                default:
                    // Nothing for other cases.
            }
        }

        @Override
        /**
         * Implements two behaviors: The type of a shift is the type of its LHS, and determining the
         * type of an operation one of whose arguments is @Literal or @ConstantPositive
         */
        public Void visitBinary(BinaryTree tree, AnnotatedTypeMirror type) {

            Tree lhs = tree.getLeftOperand();
            Tree rhs = tree.getLeftOperand();
            AnnotatedTypeMirror lht = getAnnotatedType(lhs);
            AnnotatedTypeMirror rht = getAnnotatedType(rhs);

            if (isLiteralOrConstantPositive(lht) && isLiteralOrConstantPositive(rht)) {
                // Special rules for {@Literal, @Constant} op {@Literal, @Constant}

                switch (tree.getKind()) {
                    case LEFT_SHIFT: // left shift <<
                    case RIGHT_SHIFT: // right shift >>
                    case UNSIGNED_RIGHT_SHIFT: // unsigned right shift >>>
                        // The type of a shift is the type of its LHS.
                        AnnotatedTypeMirror lht = getAnnotatedType(tree.getLeftOperand());
                        type.replaceAnnotations(lht.getAnnotations());
                        break;

                    case AND: // bitwise and logical "and" &
                    case OR: // bitwise and logical "or" |
                        // No special

                    case CONDITIONAL_AND: // conditional-and &&
                    case CONDITIONAL_OR: // conditional-or ||
                    case DIVIDE: // division /
                    case EQUAL_TO: // equal-to ==
                    case GREATER_THAN: // greater-than >
                    case GREATER_THAN_EQUAL: // greater-than-equal >=
                    case LESS_THAN: // less-than <
                    case LESS_THAN_EQUAL: // less-than-equal <=
                    case MINUS: // subtraction -
                    case MULTIPLY: // multiplication *
                    case NOT_EQUAL_TO: // not-equal-to !=
                    case PLUS: // addition or string concatenation +
                    case REMAINDER: // remainder %
                    case XOR: // bitwise and logical "xor" ^

                    default:
                        // Do nothing
                }
                annotateBoolean(type);
                return null;
            } else {
                // The type of a shift is the type of its LHS.
                switch (tree.getKind()) {
                    case LEFT_SHIFT: // left shift <<
                    case RIGHT_SHIFT: // right shift >>
                    case UNSIGNED_RIGHT_SHIFT: // unsigned right shift >>>
                        // The type of a shift is the type of its LHS.
                        AnnotatedTypeMirror lht = getAnnotatedType(tree.getLeftOperand());
                        type.replaceAnnotations(lht.getAnnotations());
                        break;

                    default:
                        // Do nothing
                }
                annotateBoolean(type);
                return null;
            }
        }

        /**
         * Returns true if the type is @Literal or @ConstantPositive.
         *
         * @param atm the type to test
         * @return true if the type is @Literal or @ConstantPositive
         */
        boolean isLiteralOrConstantPositive(AnnotatedTypeMirror atm) {
            throw new Error("not yet implemented");
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, AnnotatedTypeMirror type) {
            annotateBoolean(type);
            return null;
        }
    }
}
