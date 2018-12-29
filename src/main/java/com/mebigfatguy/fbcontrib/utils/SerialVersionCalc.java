/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

public class SerialVersionCalc {

	enum ModifierType {
		CLASS, METHOD, FIELD
	}

	public static long uuid(JavaClass cls) throws IOException {

		if (cls.isEnum()) {
			return 0;
		}

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");

			utfUpdate(digest, cls.getClassName());
			digest.update(toArray(filterModifiers(cls.getModifiers(), ModifierType.CLASS)));

			String[] infs = cls.getInterfaceNames();
			Arrays.sort(infs);
			Arrays.stream(infs).forEach(inf -> utfUpdate(digest, inf));

			Field[] fields = cls.getFields();
			Arrays.sort(fields, new FieldSorter());
			Arrays.stream(fields).filter(field -> !field.isPrivate() || (!field.isStatic() && !field.isTransient()))
					.forEach(field -> {
						utfUpdate(digest, field.getName());
						digest.update(toArray(filterModifiers(field.getModifiers(), ModifierType.FIELD)));
						utfUpdate(digest, field.getSignature());
					});

			Method[] methods = cls.getMethods();
			Arrays.sort(methods, new MethodSorter());

			Arrays.stream(methods).filter(method -> "<clinit>".equals(method.getName())).limit(1).forEach(sinit -> {
				utfUpdate(digest, "<clinit>");
				digest.update(toArray(Const.ACC_STATIC));
				utfUpdate(digest, "()V");
			});

			Arrays.stream(methods).filter(method -> "<init>".equals(method.getName()) && !method.isPrivate())
					.forEach(init -> {
						utfUpdate(digest, "<init>");
						digest.update(toArray(filterModifiers(init.getModifiers(), ModifierType.METHOD)));
						utfUpdate(digest, init.getSignature().replace('/', '.')); // how bazaar
					});

			Arrays.stream(methods).filter(method -> !"<clinit>".equals(method.getName())
					&& !"<init>".equals(method.getName()) && !method.isPrivate()).forEach(cons -> {
						utfUpdate(digest, cons.getName());
						digest.update(toArray(filterModifiers(cons.getModifiers(), ModifierType.METHOD)));
						utfUpdate(digest, method.getSignature().replace('/', '.')); // how bazaar
					});

			byte[] shaBytes = digest.digest();

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

	private static byte[] toArray(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(i);
		return b.array();
	}

	private static void utfUpdate(MessageDigest digest, String str) {
		byte[] data = str.getBytes(StandardCharsets.UTF_8);

		digest.update(toArray(data.length), 2, 2);
		digest.update(data);
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
