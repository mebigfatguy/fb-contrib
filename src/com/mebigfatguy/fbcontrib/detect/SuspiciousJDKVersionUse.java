/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Dave Brosius
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
package com.mebigfatguy.fbcontrib.detect;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for calls to classes and methods that do not exist in the JDK for which this class is
 * compiled. This can happen if you specify the -source and -target options of the javac compiler, and
 * specify a target that is less than the jdk version of the javac compiler.
 */
public class SuspiciousJDKVersionUse extends BytecodeScanningDetector
{
	private static final Map<Integer, String> VER_REG_EX = new HashMap<Integer, String>();
	static {
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_1), "(jdk|j2?re)1.1");
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_2), "(jdk|j2?re)1.2");
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_3), "(jdk|j2?re)1.3");
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_4), "(jdk|j2?re)1.4");
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_5), "((jdk|j2?re)1.5)|(java-5)");
		VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_6), "((jdk|j2?re)1.6)|(java-6)");
        VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_7), "((jdk|j2?re)1.7)|(java-7)");
        VER_REG_EX.put(Integer.valueOf(Constants.MAJOR_1_8), "((jdk|j2?re)1.8)|(java-8)");
	}
	private static final Map<Integer, Integer> HUMAN_VERSIONS = new HashMap<Integer, Integer>();
	static {
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_1), Values.ONE);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_2), Values.TWO);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_3), Values.THREE);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_4), Values.FOUR);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_5), Values.FIVE);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_6), Values.SIX);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_7), Values.SEVEN);
		HUMAN_VERSIONS.put(Integer.valueOf(Constants.MAJOR_1_8), Values.EIGHT);
	}
	private static final Pattern jarPattern = Pattern.compile("jar:file:/*([^!]*)");
	private static final String SJVU_JDKHOME = "fb-contrib.sjvu.jdkhome";

	private final Map<String, File> versionPaths;
	private final Map<Integer, Map<String, Set<String>>> validMethodsByVersion;
	private final Map<String, String> superNames;
	private final Map<Integer, ZipFile> jdkZips;
	private File jdksRoot = null;
	private Integer clsMajorVersion;
	private ZipFile jdkZip;
	private final BugReporter bugReporter;

	public SuspiciousJDKVersionUse(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		versionPaths = new HashMap<String, File>();
		jdkZips = new HashMap<Integer, ZipFile>();
		validMethodsByVersion = new HashMap<Integer, Map<String, Set<String>>>();
		superNames = new HashMap<String, String>();
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			clsMajorVersion = Integer.valueOf(classContext.getJavaClass().getMajor());
			jdkZip = jdkZips.get(clsMajorVersion);
			if (jdkZip == null) {
				File rtJar = getRTJarFile();
				if (rtJar == null)
					rtJar = getRTJarFromProperty(clsMajorVersion);
				if (rtJar != null) {
					jdkZip = new ZipFile(rtJar);
					jdkZips.put(clsMajorVersion,  jdkZip);
				}
			}
			
			if (jdkZip == null)
				return;

			super.visitClassContext(classContext);
		} catch (IOException ioe) {
			//Hmm What to do
		} finally {
			clsMajorVersion = null;
			jdkZip = null;
		}
	}

	@Override
	public void sawOpcode(int seen) {

		String clsName;
		try {
			if ((seen == INVOKEVIRTUAL) //Interfaces are more difficult, ignore for now
			||  (seen == INVOKESTATIC)
			||  (seen == INVOKESPECIAL)) {
				clsName = getClassConstantOperand();
				if ((clsName.startsWith("java/"))
				||  (clsName.startsWith("javax/"))) {
					Method m = findCalledMethod();
					if (m == null)
						return;

					Map<String, Set<String>> validMethods = validMethodsByVersion.get(clsMajorVersion);
					if (validMethods == null) {
						validMethods = new HashMap<String, Set<String>>();
						validMethodsByVersion.put(clsMajorVersion, validMethods);
					}

					if (!isValid(validMethods, clsName)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SJVU_SUSPICIOUS_JDK_VERSION_USE.name(), HIGH_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this)
								   .addCalledMethod(this));
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} catch (IOException ioe) {
			//Hmm what do do.
		}
	}

	private Method findCalledMethod() {
		try {
			JavaClass clss = Repository.lookupClass(getClassConstantOperand());
			Method[] methods = clss.getMethods();
			String calledMethod = getNameConstantOperand();
			String calledSignature = getSigConstantOperand();
			for (Method m : methods) {
				if (m.getName().equals(calledMethod) && m.getSignature().equals(calledSignature)) {
					return m;
				}
			}

			return null;
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			return null;
		}
	}

	private boolean isValid(Map<String, Set<String>> validMethods, String clsName) throws IOException, ClassNotFoundException {
		InputStream is = null;

		try {
			Set<String> methodInfos = validMethods.get(clsName);
			if (methodInfos == null) {

				ZipEntry ze = jdkZip.getEntry(clsName + ".class");
				if (ze != null) {
					is = new BufferedInputStream(jdkZip.getInputStream(ze));
					ClassParser parser = new ClassParser(is, clsName);
					JavaClass calledClass = parser.parse();

					superNames.put(clsName, calledClass.getSuperclassName().replace('.', '/'));
					Method[] methods = calledClass.getMethods();

					methodInfos = new HashSet<String>(methods.length);
					validMethods.put(clsName, methodInfos);

					for (Method m : methods) {
						methodInfos.add(m.getName() + m.getSignature());
					}
				} else if (clsName.startsWith("java/")) {
					bugReporter.reportBug(new BugInstance(this, BugType.SJVU_SUSPICIOUS_JDK_VERSION_USE.name(), HIGH_PRIORITY)
							   .addClass(this)
							   .addMethod(this)
							   .addSourceLine(this)
							   .addClass(clsName));
				}
			}

			if (methodInfos != null) {
				String wantedMethod = getNameConstantOperand() + getSigConstantOperand();
				if (methodInfos.contains(wantedMethod))
					return true;
				else if ("java/lang/Object".equals(clsName))
					return false;
				else
					return isValid(validMethods, superNames.get(clsName));
			}

			return true;
		}
		finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	private File getRTJarFile(){
		String versionStr = VER_REG_EX.get(clsMajorVersion);
		if (versionStr == null)
			return null;

		File rtPath = versionPaths.get(versionStr);
		if (rtPath != null)
			return rtPath;
		if (versionPaths.containsKey(versionStr))
			return null;

		if (jdksRoot == null) {
			URL jdkUrl = SuspiciousJDKVersionUse.class.getResource("/java/lang/Object.class");
			if (jdkUrl != null) {
				Matcher m = jarPattern.matcher(jdkUrl.toExternalForm());

				if (m.find()) {
					String path = m.group(1);
					jdksRoot = new File(path);
					Pattern verPat = Pattern.compile(versionStr);
					m = verPat.matcher(jdksRoot.getName());
					while ((jdksRoot.getParentFile() != null) && !m.find()) {
						jdksRoot = jdksRoot.getParentFile();
						m = verPat.matcher(jdksRoot.getName());
					}

					if (jdksRoot.getParentFile() == null) {
						versionPaths.put(versionStr, null);
						return null;
					}

					try {
						String encoding = System.getProperty("file.encoding");
						jdksRoot = new File(URLDecoder.decode(jdksRoot.getParentFile().getPath(), encoding));
					} catch (UnsupportedEncodingException uee) {
						versionPaths.put(versionStr, null);
						return null;
					}
				}
			}
		}

		if (jdksRoot != null) {
			File[] possibleJdks = jdksRoot.listFiles();
			if (possibleJdks != null) {
				for (File possibleJdk : possibleJdks) {
					Pattern verPat = Pattern.compile(versionStr);
					Matcher m = verPat.matcher(possibleJdk.getName());
					if (m.find()) {
						File wantedRtJar = new File(possibleJdk, "lib/rt.jar");
						if (!wantedRtJar.exists()) {
							wantedRtJar = new File(possibleJdk, "jre/lib/rt.jar");
							if (!wantedRtJar.exists()) {
								versionPaths.put(versionStr, null);
								return null;
							}
						}
						versionPaths.put(versionStr, wantedRtJar);
						return wantedRtJar;
					}
				}
			}
		}
		
		versionPaths.put(versionStr, null);

		return null;
	}

	private static File getRTJarFromProperty(int requestedVersion) {
		String jdkHome = System.getProperty(SJVU_JDKHOME + "." + HUMAN_VERSIONS.get(requestedVersion));
		if (jdkHome == null)
			return null;

		File rtJar = new File(jdkHome, "lib/rt.jar");
		if (rtJar.exists())
			return rtJar;
		rtJar = new File(jdkHome, "jre/lib/rt.jar");
		if (rtJar.exists())
			return rtJar;

		return null;
	}
}
