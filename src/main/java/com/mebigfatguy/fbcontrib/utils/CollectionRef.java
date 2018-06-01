package com.mebigfatguy.fbcontrib.utils;

import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.OpcodeStack;

import java.util.Objects;

public class CollectionRef {
    private int register;
    private XField field;

    public CollectionRef(OpcodeStack.Item itm) {
        int reg = itm.getRegisterNumber();
        if (reg >= 0) {
            register = reg;
        } else {
            XField xf = itm.getXField();
            if (xf != null) {
                field = xf;
            }
            register = -1;
        }
    }

    @Override
    public int hashCode() {
        if (register >= 0) {
            return register;
        }

        if (field != null) {
            return field.hashCode();
        }

        return Integer.MAX_VALUE;
    }

    public boolean isValid() {
        return (register >= 0) || (field != null);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CollectionRef)) {
            return false;
        }

        CollectionRef that = (CollectionRef) o;

        return (register == that.register) && Objects.equals(field, that.field);
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }
}
