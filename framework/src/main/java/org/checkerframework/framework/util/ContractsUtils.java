package org.checkerframework.framework.util;

import com.sun.tools.javac.code.Attribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.util.ElementFilter;
import org.checkerframework.framework.qual.ConditionalPostconditionAnnotation;
import org.checkerframework.framework.qual.EnsuresQualifier;
import org.checkerframework.framework.qual.EnsuresQualifierIf;
import org.checkerframework.framework.qual.EnsuresQualifiers;
import org.checkerframework.framework.qual.EnsuresQualifiersIf;
import org.checkerframework.framework.qual.PostconditionAnnotation;
import org.checkerframework.framework.qual.PreconditionAnnotation;
import org.checkerframework.framework.qual.QualifierArgument;
import org.checkerframework.framework.qual.RequiresQualifier;
import org.checkerframework.framework.qual.RequiresQualifiers;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;

/**
 * A utility class to handle pre- and postconditions.
 *
 * @see PreconditionAnnotation
 * @see RequiresQualifier
 * @see PostconditionAnnotation
 * @see EnsuresQualifier
 * @see EnsuresQualifierIf
 */
// TODO: This class assumes that most annotations have a field named "expression".
// If not, issue a more helpful error message rather than a crash.
public class ContractsUtils {

    /**
     * The currently-used ContractsUtils object. This class is NOT a singleton: this value can
     * change.
     */
    protected static ContractsUtils instance;

    /** The factory that this ContractsUtils is associated with. */
    protected GenericAnnotatedTypeFactory<?, ?, ?, ?> factory;

    /** Creates a ContractsUtils for the given factory. */
    private ContractsUtils(GenericAnnotatedTypeFactory<?, ?, ?, ?> factory) {
        this.factory = factory;
    }

    /** Returns an instance of the {@link ContractsUtils} class. */
    public static ContractsUtils getInstance(GenericAnnotatedTypeFactory<?, ?, ?, ?> factory) {
        if (instance == null || instance.factory != factory) {
            instance = new ContractsUtils(factory);
        }
        return instance;
    }

    /**
     * A contract represents an annotation on an expression, along with the kind: precondition,
     * postcondition, or conditional postcondition.
     */
    public abstract static class Contract {

        public enum Kind {
            PRECONDITION("precondition"),
            POSTCONDTION("postcondition"),
            CONDITIONALPOSTCONDTION("conditional.postcondition");
            public final String errorKey;

            Kind(String errorKey) {
                this.errorKey = errorKey;
            }
        }

        /**
         * The expression for which the condition must hold, such as {@code "foo"} in
         * {@code @RequiresNonNull("foo")}.
         */
        public final String expression;

        /** The annotation that must be on the type of expression as part of this contract. */
        public final AnnotationMirror annotation;

        /** The annotation that expressed this contract; used for diagnostic messages. */
        public final AnnotationMirror contractAnnotation;

        /** The kind of contract: precondition, postcondition, or conditional postcondition. */
        public final Kind kind;

        /**
         * Creates a new Contract.
         *
         * @param expression the Java expression that should have a type qualifier
         * @param annotation the type qualifier that {@code expression} should have
         * @param contractAnnotation the pre- or post-condition annotation that the programmer
         *     wrote; used for diagnostic messages
         * @param kind precondition, postcondition, or conditional postcondition
         */
        public Contract(
                String expression,
                AnnotationMirror annotation,
                AnnotationMirror contractAnnotation,
                Kind kind) {
            this.expression = expression;
            this.annotation = annotation;
            this.contractAnnotation = contractAnnotation;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Contract contract = (Contract) o;

            return Objects.equals(expression, contract.expression)
                    && Objects.equals(annotation, contract.annotation)
                    && kind == contract.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression, annotation, kind);
        }
    }

    /** A precondition contract. */
    public static class Precondition extends Contract {
        /**
         * Create a precondition contract.
         *
         * @param expression the Java expression that should have a type qualifier
         * @param annotation the type qualifier that {@code expression} should have
         * @param contractAnnotation the precondition annotation that the programmer wrote; used for
         *     diagnostic messages
         */
        public Precondition(
                String expression,
                AnnotationMirror annotation,
                AnnotationMirror contractAnnotation) {
            super(expression, annotation, contractAnnotation, Kind.PRECONDITION);
        }
    }

    /** A postcondition contract. */
    public static class Postcondition extends Contract {
        /**
         * Create a postcondition contract.
         *
         * @param expression the Java expression that should have a type qualifier
         * @param annotation the type qualifier that {@code expression} should have
         * @param contractAnnotation the postcondition annotation that the programmer wrote; used
         *     for diagnostic messages
         */
        public Postcondition(
                String expression,
                AnnotationMirror annotation,
                AnnotationMirror contractAnnotation) {
            super(expression, annotation, contractAnnotation, Kind.POSTCONDTION);
        }
    }

    /**
     * Represents a conditional postcondition that must be verified by {@code BaseTypeVisitor} or
     * one of its subclasses. Automatically extracted from annotations with meta-annotation
     * {@code @ConditionalPostconditionAnnotation}, such as {@code EnsuresNonNullIf}.
     */
    public static class ConditionalPostcondition extends Contract {

        /**
         * The return value for the annotated method that ensures that the conditional postcondition
         * holds. For example, given<br>
         * {@code @EnsuresNonNullIf(expression="foo", result=false) boolean method()}<br>
         * {@code foo} is guaranteed to be {@code @NonNull} after a call to {@code method()} if that
         * call returns {@code false}.
         */
        public final boolean annoResult;

        /**
         * Create a new conditional postcondition.
         *
         * @param expression the Java expression that should have a type qualifier
         * @param annoResult whether the condition is the method returning true or false
         * @param annotation the type qualifier that {@code expression} should have
         * @param contractAnnotation the postcondition annotation that the programmer wrote; used
         *     for diagnostic messages
         */
        public ConditionalPostcondition(
                String expression,
                boolean annoResult,
                AnnotationMirror annotation,
                AnnotationMirror contractAnnotation) {
            super(expression, annotation, contractAnnotation, Kind.CONDITIONALPOSTCONDTION);
            this.annoResult = annoResult;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            ConditionalPostcondition that = (ConditionalPostcondition) o;
            return annoResult == that.annoResult;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), annoResult);
        }
    }

    public List<Contract> getContracts(ExecutableElement element) {
        List<Contract> contracts = new ArrayList<>();
        contracts.addAll(getPreconditions(element));
        contracts.addAll(getPostconditions(element));
        contracts.addAll(getConditionalPostconditions(element));
        return contracts;
    }

    /** Returns the set of preconditions on the element {@code element}. */
    public Set<Precondition> getPreconditions(Element element) {
        Set<Precondition> result = new LinkedHashSet<>();
        // Check for a single contract annotation.
        AnnotationMirror requiresQualifier =
                factory.getDeclAnnotation(element, RequiresQualifier.class);
        result.addAll(getPrecondition(requiresQualifier));

        // Check for a wrapper around contract annotations.
        AnnotationMirror requiresQualifiers =
                factory.getDeclAnnotation(element, RequiresQualifiers.class);
        if (requiresQualifiers != null) {
            List<AnnotationMirror> requiresQualifierList =
                    AnnotationUtils.getElementValueArray(
                            requiresQualifiers, "value", AnnotationMirror.class, false);
            for (AnnotationMirror a : requiresQualifierList) {
                result.addAll(getPrecondition(a));
            }
        }

        // Check for type-system specific annotations.
        List<Pair<AnnotationMirror, AnnotationMirror>> declAnnotations =
                factory.getDeclAnnotationWithMetaAnnotation(element, PreconditionAnnotation.class);
        for (Pair<AnnotationMirror, AnnotationMirror> r : declAnnotations) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;
            AnnotationMirror precondAnno = getAnnotationMirrorOfContractAnnotation(metaAnno, anno);
            if (precondAnno == null) {
                continue;
            }
            List<String> expressions =
                    AnnotationUtils.getElementValueArray(anno, "value", String.class, false);
            for (String expr : expressions) {
                result.add(new Precondition(expr, precondAnno, anno));
            }
        }
        return result;
    }

    /**
     * Returns the preconditions expressed by the given annotation.
     *
     * @param requiresQualifier a {@link RequiresQualifier}, or null
     * @return the preconditions expressed by the given annotation, or the empty set if the argument
     *     is null
     */
    private Set<Precondition> getPrecondition(AnnotationMirror requiresQualifier) {
        if (requiresQualifier == null) {
            return Collections.emptySet();
        }
        AnnotationMirror precondAnno = getAnnotationMirrorOfContractAnnotation(requiresQualifier);
        if (precondAnno == null) {
            return Collections.emptySet();
        }
        Set<Precondition> result = new LinkedHashSet<>();
        List<String> expressions =
                AnnotationUtils.getElementValueArray(
                        requiresQualifier, "expression", String.class, false);
        for (String expr : expressions) {
            result.add(new Precondition(expr, precondAnno, requiresQualifier));
        }
        return result;
    }

    /** Returns the set of postconditions on the method {@code methodElement}. */
    public Set<Postcondition> getPostconditions(ExecutableElement methodElement) {
        Set<Postcondition> result = new LinkedHashSet<>();
        // Check for a single contract annotation.
        AnnotationMirror ensuresQualifier =
                factory.getDeclAnnotation(methodElement, EnsuresQualifier.class);
        result.addAll(getPostcondition(ensuresQualifier));

        // Check for a wrapper around contract annotations.
        AnnotationMirror ensuresQualifiers =
                factory.getDeclAnnotation(methodElement, EnsuresQualifiers.class);
        if (ensuresQualifiers != null) {
            List<AnnotationMirror> ensuresQualifiersList =
                    AnnotationUtils.getElementValueArray(
                            ensuresQualifiers, "value", AnnotationMirror.class, false);
            for (AnnotationMirror a : ensuresQualifiersList) {
                result.addAll(getPostcondition(a));
            }
        }

        // Check for type-system specific annotations.
        List<Pair<AnnotationMirror, AnnotationMirror>> declAnnotations =
                factory.getDeclAnnotationWithMetaAnnotation(
                        methodElement, PostconditionAnnotation.class);
        for (Pair<AnnotationMirror, AnnotationMirror> r : declAnnotations) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;

            result.addAll(getPostConditionAnnotations(anno, metaAnno));
        }

        return result;
    }

    /**
     * Returns multiple postcondition annotations or a single postcondition annotation in a set.
     *
     * @param anno wrapper annotation of multiple postcondition annotations, or a single post
     *     condition annotation
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     */
    private Set<Postcondition> getPostConditionAnnotations(
            AnnotationMirror anno, AnnotationMirror metaAnno) {
        @SuppressWarnings("unchecked")
        List<AnnotationValue> annoValue =
                AnnotationUtils.getElementValue(anno, "value", List.class, false);
        // All elements of list "annoValue" will contain either instance of AnnotationMirror or of
        // Attribute.Constant, therefore we can determine the annotaion by checking the first
        // element
        if (annoValue.get(0) instanceof AnnotationMirror) {
            // Check for multiple contracts
            return getMultiplePostconditionAnnotations(metaAnno, annoValue);
        } else if (annoValue.get(0) instanceof Attribute.Constant) {
            // The value of an annotation element of type String is Attribute.Constant
            // Check for a single contract
            return getSinglePostConditionAnnotation(anno, metaAnno, annoValue);
        } else {
            throw new BugInCF(
                    "Unexpected value %s [%s] in %s",
                    annoValue.get(0), annoValue.get(0).getClass(), anno);
        }
    }

    /**
     * Add multiple postcondition annotations in a set and returns them.
     *
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     * @param annoValue the value of wrapper annotation containing multiple postcondition
     *     annotations
     */
    private Set<Postcondition> getMultiplePostconditionAnnotations(
            AnnotationMirror metaAnno, List<AnnotationValue> annoValue) {
        Set<Postcondition> result = new LinkedHashSet<>();
        for (AnnotationValue av : annoValue) {
            AnnotationMirror am = (AnnotationMirror) av.getValue();
            AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(metaAnno, am);
            if (postcondAnno == null) {
                continue;
            }
            List<String> expression =
                    AnnotationUtils.getElementValueArray(am, "value", String.class, false);
            for (String expr : expression) {
                result.add(new Postcondition(expr, postcondAnno, am));
            }
        }
        return result;
    }

    /**
     * Add a single postcondition annotation in a set and returns them.
     *
     * @param anno single post condition annotation
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     * @param annoValue the value of postcondition annotation
     */
    private Set<Postcondition> getSinglePostConditionAnnotation(
            AnnotationMirror anno, AnnotationMirror metaAnno, List<AnnotationValue> annoValue) {
        List<String> expressions = new ArrayList<>();
        for (AnnotationValue a : annoValue) {
            expressions.add((String) a.getValue());
        }
        Set<Postcondition> result = new LinkedHashSet<>();
        AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(metaAnno, anno);
        if (postcondAnno != null) {
            for (String expr : expressions) {
                result.add(new Postcondition(expr, postcondAnno, anno));
            }
        }
        return result;
    }

    /**
     * Returns the postconditions expressed by the given annotation.
     *
     * @param ensuresQualifier an {@link EnsuresQualifier}, or null
     * @return the postconditions expostssed by the given annotation, or the empty set if the
     *     argument is null
     */
    private Set<Postcondition> getPostcondition(AnnotationMirror ensuresQualifier) {
        if (ensuresQualifier == null) {
            return Collections.emptySet();
        }
        AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(ensuresQualifier);
        if (postcondAnno == null) {
            return Collections.emptySet();
        }
        Set<Postcondition> result = new LinkedHashSet<>();
        List<String> expressions =
                AnnotationUtils.getElementValueArray(
                        ensuresQualifier, "expression", String.class, false);
        for (String expr : expressions) {
            result.add(new Postcondition(expr, postcondAnno, ensuresQualifier));
        }
        return result;
    }

    /** Returns the conditional postconditions on the method {@code methodElement}. */
    public Set<ConditionalPostcondition> getConditionalPostconditions(
            ExecutableElement methodElement) {
        Set<ConditionalPostcondition> result = new LinkedHashSet<>();
        // Check for a single contract annotation.
        AnnotationMirror ensuresQualifierIf =
                factory.getDeclAnnotation(methodElement, EnsuresQualifierIf.class);
        result.addAll(getConditionalPostcondition(ensuresQualifierIf));

        // Check for a wrapper around contract annotations.
        AnnotationMirror ensuresQualifiersIf =
                factory.getDeclAnnotation(methodElement, EnsuresQualifiersIf.class);
        if (ensuresQualifiersIf != null) {
            List<AnnotationMirror> annotations =
                    AnnotationUtils.getElementValueArray(
                            ensuresQualifiersIf, "value", AnnotationMirror.class, false);
            for (AnnotationMirror a : annotations) {
                result.addAll(getConditionalPostcondition(a));
            }
        }

        // Check for type-system-specific annotations.
        List<Pair<AnnotationMirror, AnnotationMirror>> declAnnotations =
                factory.getDeclAnnotationWithMetaAnnotation(
                        methodElement, ConditionalPostconditionAnnotation.class);
        for (Pair<AnnotationMirror, AnnotationMirror> r : declAnnotations) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;

            result.addAll(getConditionalPostConditionAnnotations(anno, metaAnno));
        }

        return result;
    }

    /**
     * Returns true if the given AnnotationMirror has an element named {@code value}.
     *
     * @param anno the AnnotationMirror whose fields to search
     * @return true if {@code anno} has an element named {@code value}
     */
    private boolean hasValueElement(AnnotationMirror anno) {
        for (ExecutableElement elem : anno.getElementValues().keySet()) {
            if (elem.getSimpleName().contentEquals("value")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns conditional postcondition annotations or a single conditional postcondition
     * annotation.
     *
     * @param anno wrapper annotation of multiple conditional postcondition annotations, or a single
     *     conditional postcondition annotation
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     */
    private Set<ConditionalPostcondition> getConditionalPostConditionAnnotations(
            AnnotationMirror anno, AnnotationMirror metaAnno) {
        if (hasValueElement(anno)) {
            // Check for multiple contracts
            return getMultipleConditionalPostConditionAnnotations(anno, metaAnno);
        } else {
            // Check for a single contract
            return getSingleConditionalPostConditionAnnotation(anno, metaAnno);
        }
    }

    /**
     * Add multiple conditional postcondition annotations in a set and returns them.
     *
     * @param anno wrapper annotation of multiple conditional postcondition annotations
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     */
    private Set<ConditionalPostcondition> getMultipleConditionalPostConditionAnnotations(
            AnnotationMirror anno, AnnotationMirror metaAnno) {
        @SuppressWarnings("unchecked")
        List<AnnotationValue> annoValue =
                AnnotationUtils.getElementValue(anno, "value", List.class, false);
        List<AnnotationMirror> annotations = new ArrayList<>(annoValue.size());
        for (AnnotationValue a : annoValue) {
            if (a instanceof AnnotationMirror) annotations.add((AnnotationMirror) a.getValue());
        }
        Set<ConditionalPostcondition> result = new LinkedHashSet<>();
        for (AnnotationMirror a : annotations) {
            AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(metaAnno, a);
            if (postcondAnno == null) {
                continue;
            }
            boolean annoResult = AnnotationUtils.getElementValue(a, "result", Boolean.class, false);
            List<String> expressions =
                    AnnotationUtils.getElementValueArray(a, "expression", String.class, false);
            for (String expr : expressions) {
                result.add(new ConditionalPostcondition(expr, annoResult, postcondAnno, a));
            }
        }
        return result;
    }

    /**
     * Add single conditional postcondition annotation in a set and returns them.
     *
     * @param anno single conditional postcondition annotation
     * @param metaAnno used to return the annotation mirror as specified by the element in this
     *     meta-annotation
     */
    private Set<ConditionalPostcondition> getSingleConditionalPostConditionAnnotation(
            AnnotationMirror anno, AnnotationMirror metaAnno) {
        @SuppressWarnings("unchecked")
        List<AnnotationValue> annoValue =
                AnnotationUtils.getElementValue(anno, "expression", List.class, false);
        List<String> expressions = new ArrayList<>(annoValue.size());
        for (AnnotationValue a : annoValue) {
            // The value of an annotation element of type String is Attribute.Constant
            if (a instanceof Attribute.Constant) expressions.add((String) a.getValue());
        }
        AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(metaAnno, anno);
        Set<ConditionalPostcondition> result = new LinkedHashSet<>();
        if (postcondAnno != null) {
            boolean annoResult =
                    AnnotationUtils.getElementValue(anno, "result", Boolean.class, false);
            for (String expr : expressions) {
                result.add(new ConditionalPostcondition(expr, annoResult, postcondAnno, anno));
            }
        }
        return result;
    }

    /**
     * Returns a set of triples {@code (expr, result, annotation)} of conditional postconditions
     * that are expressed in the source code using the given postcondition annotation.
     */
    private Set<ConditionalPostcondition> getConditionalPostcondition(
            AnnotationMirror ensuresQualifierIf) {
        if (ensuresQualifierIf == null) {
            return Collections.emptySet();
        }
        AnnotationMirror postcondAnno = getAnnotationMirrorOfContractAnnotation(ensuresQualifierIf);
        if (postcondAnno == null) {
            return Collections.emptySet();
        }
        Set<ConditionalPostcondition> result = new LinkedHashSet<>();
        List<String> expressions =
                AnnotationUtils.getElementValueArray(
                        ensuresQualifierIf, "expression", String.class, false);
        boolean annoResult =
                AnnotationUtils.getElementValue(ensuresQualifierIf, "result", Boolean.class, false);
        for (String expr : expressions) {
            result.add(
                    new ConditionalPostcondition(
                            expr, annoResult, postcondAnno, ensuresQualifierIf));
        }
        return result;
    }

    /// Helper methods

    /**
     * Returns the annotation mirror as specified by the "qualifier" element in {@code
     * contractAnno}. If {@code argumentAnno} is specified, then arguments are copied from {@code
     * argumentAnno} to the returned annotation, renamed according to {@code argumentRenaming}.
     *
     * <p>This is a helper method intended to be called from {@link
     * getAnnotationMirrorOfContractAnnotation} and {@link getAnnotationMirrorOfContractAnnotation}.
     * Use one of those methods if possible.
     *
     * @param contractAnno a contract annotation, which has a {@code qualifier} element
     * @param argumentAnno annotation containing the argument values, or {@code null}
     * @param argumentRenaming renaming of argument names, which maps from names in {@code
     *     argumentAnno} to names used in the returned annotation, or {@code null}
     * @return a qualifier whose type is that of {@code contract.qualifier}, or an alias for it, or
     *     null if it is not a supported qualifier of the type system
     */
    private AnnotationMirror getAnnotationMirrorOfQualifier(
            AnnotationMirror contractAnno,
            AnnotationMirror argumentAnno,
            Map<String, String> argumentRenaming) {

        Name c = AnnotationUtils.getElementValueClassName(contractAnno, "qualifier", false);

        AnnotationMirror anno;
        if (argumentAnno == null || argumentRenaming.isEmpty()) {
            // If there are no arguments, use factory method that allows caching
            anno = AnnotationBuilder.fromName(factory.getElementUtils(), c);
        } else {
            AnnotationBuilder builder = new AnnotationBuilder(factory.getProcessingEnv(), c);
            builder.copyRenameElementValuesFromAnnotation(argumentAnno, argumentRenaming);
            anno = builder.build();
        }

        if (factory.isSupportedQualifier(anno)) {
            return anno;
        } else {
            AnnotationMirror aliasedAnno = factory.canonicalAnnotation(anno);
            if (factory.isSupportedQualifier(aliasedAnno)) {
                return aliasedAnno;
            } else {
                return null;
            }
        }
    }

    /**
     * Returns the annotation mirror as specified by the "qualifier" element in {@code
     * contractAnno}.
     */
    private AnnotationMirror getAnnotationMirrorOfContractAnnotation(
            AnnotationMirror contractAnno) {
        return getAnnotationMirrorOfQualifier(contractAnno, null, null);
    }

    /**
     * Makes a map from element names of a contract annotation to qualifier argument names, as
     * defined by {@link QualifierArgument}.
     *
     * <p>Each element of {@code contractAnnoElement} that is annotated by {@link QualifierArgument}
     * is mapped to the name specified by the value of {@link QualifierArgument}. If the value is
     * not specified or is an empty string, then the element is mapped to an argument of the same
     * name.
     *
     * @param contractAnnoElement the declaration of the contract annotation containing the elements
     * @return map from the names of elements of {@code sourceArgumentNames} to the corresponding
     *     qualifier argument names
     * @see QualifierArgument
     */
    private Map<String, String> makeArgumentMap(Element contractAnnoElement) {
        HashMap<String, String> argumentMap = new HashMap<>();
        for (ExecutableElement meth :
                ElementFilter.methodsIn(contractAnnoElement.getEnclosedElements())) {
            AnnotationMirror argumentAnnotation =
                    factory.getDeclAnnotation(meth, QualifierArgument.class);
            if (argumentAnnotation != null) {
                String sourceName = meth.getSimpleName().toString();
                String targetName =
                        AnnotationUtils.getElementValue(
                                argumentAnnotation, "value", String.class, false);
                if (targetName == null || targetName.isEmpty()) {
                    targetName = sourceName;
                }
                argumentMap.put(sourceName, targetName);
            }
        }
        return argumentMap;
    }

    /**
     * Returns the annotation mirror as specified by the "qualifier" element in {@code
     * contractAnno}, with arguments taken from {@code argumentAnno}.
     */
    private AnnotationMirror getAnnotationMirrorOfContractAnnotation(
            AnnotationMirror contractAnno, AnnotationMirror argumentAnno) {

        Map<String, String> argumentMap =
                makeArgumentMap(argumentAnno.getAnnotationType().asElement());
        return getAnnotationMirrorOfQualifier(contractAnno, argumentAnno, argumentMap);
    }
}
