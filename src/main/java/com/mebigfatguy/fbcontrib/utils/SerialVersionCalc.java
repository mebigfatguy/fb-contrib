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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

public final class SerialVersionCalc {

    enum ModifierType {
        CLASS, METHOD, FIELD
    }

    private SerialVersionCalc() {

    }

    public static long uuid(JavaClass cls) throws IOException {

        if (cls.isEnum()) {
            return 0;
        }

        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeUTF(cls.getClassName());
            dos.writeInt(filterModifiers(cls.getModifiers(), ModifierType.CLASS));

            String[] infs = cls.getInterfaceNames();
            if (infs.length > 0) {
	            Arrays.sort(infs);
	            for (String inf : infs) {
	                dos.writeUTF(inf);
	            }
            }

            Field[] fields = cls.getFields();
            if (fields.length > 0) {
	            Arrays.sort(fields, new FieldSorter());
	            for (Field field : fields) {
	                if (!field.isPrivate() || (!field.isStatic() && !field.isTransient())) {
	                    dos.writeUTF(field.getName());
	                    dos.writeInt(filterModifiers(field.getModifiers(), ModifierType.FIELD));
	                    dos.writeUTF(field.getSignature());
	                }
	            }
            }

            Method[] methods = cls.getMethods();
            if (methods.length > 0) {
	            Arrays.sort(methods, new MethodSorter());
	
	            for (Method sinit : methods) {
	                if ("<clinit>".equals(sinit.getName())) {
	                    dos.writeUTF("<clinit>");
	                    dos.writeInt(Constants.ACC_STATIC);
	                    dos.writeUTF("()V");
	                    break;
	                }
	            }
	
	            for (Method init : methods) {
	                if ("<init>".equals(init.getName()) && !init.isPrivate()) {
	                	dos.writeUTF("<init>");
	                    dos.writeInt(filterModifiers(init.getModifiers(), ModifierType.METHOD));
	                    dos.writeUTF(init.getSignature().replace('/', '.')); // how bazaar
	                }
	            }
	
	            for (Method method : methods) {
	                if (!"<clinit>".equals(method.getName()) && !"<init>".equals(method.getName()) && !method.isPrivate()) {
	                    dos.writeUTF(method.getName());
	                    dos.writeInt(filterModifiers(method.getModifiers(), ModifierType.METHOD));
	                    dos.writeUTF(method.getSignature().replace('/', '.')); // how bazaar
	                }
	            }
            }

            dos.flush();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] shaBytes = digest.digest(baos.toByteArray());

            ByteBuffer bb = ByteBuffer.wrap(shaBytes, 0, 8);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            return bb.getLong();

        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }

    private static int filterModifiers(int modifier, ModifierType type) {

        switch (type) {
        case CLASS:
            return modifier & (Const.ACC_PUBLIC | Const.ACC_FINAL | Const.ACC_INTERFACE | Const.ACC_ABSTRACT);

        case METHOD:
            return modifier
                    & (Const.ACC_PUBLIC | Const.ACC_PRIVATE | Const.ACC_PROTECTED | Const.ACC_STATIC | Const.ACC_FINAL
                            | Const.ACC_SYNCHRONIZED | Const.ACC_NATIVE | Const.ACC_ABSTRACT | Const.ACC_STRICT);

        case FIELD:
            return modifier & (Const.ACC_PUBLIC | Const.ACC_PRIVATE | Const.ACC_PROTECTED | Const.ACC_STATIC
                    | Const.ACC_FINAL | Const.ACC_VOLATILE | Const.ACC_TRANSIENT);

        default:
            return 0;
        }

    }

    static class FieldSorter implements Comparator<Field> {

        @Override
        public int compare(Field f1, Field f2) {
            return f1.getName().compareTo(f2.getName());
        }
    }

    static class MethodSorter implements Comparator<Method> {

        @Override
        public int compare(Method m1, Method m2) {
            int cmp = m1.getName().compareTo(m2.getName());
            if (cmp != 0) {
                return cmp;
            }

            return m1.getSignature().compareTo(m2.getSignature());
        }
    }
}
