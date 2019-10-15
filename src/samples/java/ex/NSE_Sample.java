package ex;

public class NSE_Sample {
    class One {
        private int o;

        @Override
        public boolean equals(Object that) {
            if (that instanceof One) {
                return o == ((One) that).o;
            } else if (that instanceof Two) {
                return o == ((Two) that).t;
            }

            return false;
        }
    }

    class Two {
        public int t;

        @Override
        public boolean equals(Object that) {
            if (that instanceof Two) {
                return t == ((Two) that).t;
            }

            return false;
        }
    }

    class Parent {
        private int o;

        @Override
        public boolean equals(Object that) {
            if (that instanceof Parent) {
                return o == ((Parent) that).o;
            } else if (that instanceof Two) {
                return o == ((Child) that).t;
            }

            return false;
        }
    }

    class Child extends Parent {
        public int t;

        @Override
        public boolean equals(Object that) {
            if (that instanceof Child) {
                return t == ((Child) that).t;
            }

            return false;
        }
    }
}