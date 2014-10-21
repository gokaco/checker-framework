package org.checkerframework.checker.experimental.regex_qual;

/**
 *
 * Qualifier for the Regex type system.
 *
 * @author McArthur
 */
public class Regex {

    private Regex() { }

    public static final Regex TOP = new Regex() {
        @Override
        public String toString() {
            return "RegexTop()";
        }
    };

    public static final Regex BOTTOM = new Regex() {
        @Override
        public String toString() {
            return "RegexBot()";
        }
    };

    public static class PartialRegex extends Regex {
        private final String partialValue;

        public PartialRegex(String partialValue) {
            this.partialValue = partialValue;
        }

        public String getPartialValue() {
            return partialValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PartialRegex that = (PartialRegex) o;

            if (!partialValue.equals(that.partialValue)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return partialValue.hashCode();
        }

        @Override
        public String toString() {
            return "PartialRegex(\"" + partialValue + "\")";
        }
    }

    public static class RegexVal extends Regex {
        private final int count;
        public RegexVal(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RegexVal regexVal = (RegexVal) o;

            if (count != regexVal.count) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return count;
        }

        @Override
        public String toString() {
            return "RegexVal(" + count + ")";
        }
    }

    @Override
    public String toString() {
        return "Regex()";
    }
}
