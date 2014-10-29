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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;


/**
 * looks for methods that build xml based strings by concatenation strings
 * and custom values together. Doing so makes brittle code, that is difficult to
 * modify, validate and understand. It is cleaner to create external xml files that are
 * transformed at runtime, using parameters set through Transformer.setParameter.
 */
public class CustomBuiltXML extends BytecodeScanningDetector
{
	private static final Map<Pattern, Boolean> xmlPatterns = new HashMap<Pattern, Boolean>(10);
	static {
		xmlPatterns.put(Pattern.compile(".*<[a-zA-Z_](\\w)*>[^=]?.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*</[a-zA-Z_](\\w)*>[^=]?.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*<[a-zA-Z_](\\w)*/>[^=]?.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*<[^=]?(/)?$"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile("^(/)?>.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*=(\\s)*[\"'].*"), Boolean.FALSE);
		xmlPatterns.put(Pattern.compile("^[\"']>.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*<!\\[CDATA\\[.*", Pattern.CASE_INSENSITIVE), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*\\]\\]>.*"), Boolean.TRUE);
		xmlPatterns.put(Pattern.compile(".*xmlns:.*"), Boolean.TRUE);
	}
	private static final String CBX_MIN_REPORTABLE_ITEMS = "fb-contrib.cbx.minxmlitems";
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private int xmlItemCount = 0;
	private int xmlConfidentCount = 0;
	private int lowReportingThreshold;
	private int midReportingThreshold;
	private int highReportingThreshold;
	private int firstPC;

	/**
     * constructs a CBX detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */
	public CustomBuiltXML(BugReporter bugReporter) {
		this.bugReporter = bugReporter;

		lowReportingThreshold = Integer.getInteger(CBX_MIN_REPORTABLE_ITEMS, 5).intValue();
		midReportingThreshold = lowReportingThreshold << 1;
		highReportingThreshold = lowReportingThreshold << 2;
	}

    /**
     * overrides the visitor to create and destroy the stack
     *
     * @param classContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
    	try {
	        stack = new OpcodeStack();
	        super.visitClassContext(classContext);
    	} finally {
	        stack = null;
    	}
    }

	/**
	 * overrides the visitor reset the opcode stack
	 *
	 * @param obj the code object of the currently parsed method
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		xmlItemCount = 0;
        xmlConfidentCount = 0;
		firstPC = -1;
		super.visitCode(obj);
		if ((xmlItemCount >= lowReportingThreshold)
		&&  (xmlConfidentCount > (lowReportingThreshold >> 1))) {
			bugReporter.reportBug( new BugInstance( this,
													"CBX_CUSTOM_BUILT_XML",
													(xmlItemCount >= highReportingThreshold) ? HIGH_PRIORITY :
													(xmlItemCount >= midReportingThreshold) ? NORMAL_PRIORITY :
													LOW_PRIORITY)
										.addClass(this)
										.addMethod(this)
										.addSourceLine(this, firstPC));

		}
	}

	/**
	 * overrides the visitor to find String concatenations including xml strings
	 *
	 * @param seen the opcode that is being visited
	 */
	@Override
	public void sawOpcode(int seen) {
		String strCon = null;

		try {
	        stack.precomputation(this);

			if (seen == INVOKESPECIAL) {
				String clsName = getClassConstantOperand();
				if ("java/lang/StringBuffer".equals(clsName) || "java/lang/StringBuilder".equals(clsName)) {
					String methodName = getNameConstantOperand();
					String methodSig = getSigConstantOperand();
					if (Values.CONSTRUCTOR.equals(methodName) && ("(Ljava/lang/String;)L" + clsName + ";").equals(methodSig)) {
						if (stack.getStackDepth() > 0) {
							OpcodeStack.Item itm = stack.getStackItem(0);
							strCon = (String)itm.getConstant();
						}
					}
				}
			} else if (seen == INVOKEVIRTUAL) {
				String clsName = getClassConstantOperand();
				if ("java/lang/StringBuffer".equals(clsName) || "java/lang/StringBuilder".equals(clsName)) {
					String methodName = getNameConstantOperand();
					String methodSig = getSigConstantOperand();
					if ("append".equals(methodName) && ("(Ljava/lang/String;)L" + clsName + ";").equals(methodSig)) {
						if (stack.getStackDepth() > 0) {
							OpcodeStack.Item itm = stack.getStackItem(0);
							strCon = (String)itm.getConstant();
						}
					}
				}
			}

			if (strCon != null) {
			    strCon = strCon.trim();
				if (strCon.length() == 0)
					return;

				for (Map.Entry<Pattern, Boolean> entry : xmlPatterns.entrySet()) {
					Matcher m = entry.getKey().matcher(strCon);
					if (m.matches()) {
						xmlItemCount++;
						if (entry.getValue().booleanValue())
							xmlConfidentCount++;
						if ((firstPC < 0) && (xmlConfidentCount > 0))
							firstPC = getPC();
						break;
					}
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
