/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.mebigfatguy.fbcontrib.utils;

import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

/**
 * holds information about a field, including class, name and signature
 */
public final class FQField {
    private String className;
    private String fieldName;
    private String signature;

    public FQField(@SlashedClassName String className, String fieldName, String signature) {
        this.className = className;
        this.fieldName = fieldName;
        this.signature = signature;
    }

    @SlashedClassName
    public String getClassName() {
        return className;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public int hashCode() {
        return className.hashCode() ^ fieldName.hashCode() ^ signature.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FQField)) {
            return false;
        }

        FQField that = (FQField) o;

        return className.equals(that.className) && fieldName.equals(that.fieldName) && signature.equals(that.signature);
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }

}
