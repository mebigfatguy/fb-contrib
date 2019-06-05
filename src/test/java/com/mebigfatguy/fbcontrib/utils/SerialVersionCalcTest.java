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

import java.io.IOException;
import java.io.Serializable;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SerialVersionCalcTest implements Serializable {

	private static final long serialVersionUID = -3196349615192051714L;

	static final String MESSAGE = "Calculated Serial Version doesn't match defined one";

    @Test
    public void testSerialVersionUID() throws ClassNotFoundException, IOException {

        JavaClass cls = Repository.lookupClass(SerialVersionCalcTest.class);
        long uuid = SerialVersionCalc.uuid(cls);

        Assert.assertEquals(serialVersionUID, uuid, MESSAGE);
    }

    public void nop(String x) {
    }
    
    public <T extends Comparable> void useGenerics(T c) {
    }
}
