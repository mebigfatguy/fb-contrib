package com.mebigfatguy.fbcontrib.detect;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for odd usage patterns when using Lists
 */
@CustomUserValue
public class ListUsageIssues extends BytecodeScanningDetector {

    private static final FQMethod ARRAYS_ASLIST_METHOD = new FQMethod("java/util/Arrays", "asList",
            new SignatureBuilder().withParamTypes(Object[].class).withReturnType(List.class).build());
    private static final FQMethod COLLECTIONS_SINGLETONLIST_METHOD = new FQMethod("java/util/Collections", "singletonList",
            new SignatureBuilder().withParamTypes(Object.class).withReturnType(List.class).build());
    private static final FQMethod LIST_STREAM_METHOD = new FQMethod("java/util/List", "stream",
            new SignatureBuilder().withReturnType("java/util/stream/Stream").build());
    private static final FQMethod STREAM_FINDFIRST_METHOD = new FQMethod("java/util/stream/Stream", "findFirst",
            new SignatureBuilder().withReturnType("java/util/Optional").build());
    private static final FQMethod OPTIONAL_GET_METHOD = new FQMethod("java/util/Optional", "get", SignatureBuilder.SIG_VOID_TO_OBJECT);

    private static final Set<FQMethod> ADDALL_METHODS = UnmodifiableSet.create(
            new FQMethod("java/util/Collection", "addAll", new SignatureBuilder().withParamTypes(Collection.class).withReturnType(boolean.class).build()),
            new FQMethod("java/util/List", "addAll", new SignatureBuilder().withParamTypes(Collection.class).withReturnType(boolean.class).build()),
            new FQMethod("java/util/Set", "addAll", new SignatureBuilder().withParamTypes(Collection.class).withReturnType(boolean.class).build()));

    enum LUIUserValue {
        ONE_ITEM_LIST, LIST_STREAM, STREAM_OPTIONAL
    };

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int clsVersion;

    /**
     * constructs a LUI detector given the reporter to report bugs on with
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ListUsageIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            clsVersion = classContext.getJavaClass().getMajor();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        LUIUserValue userValue = null;
        try {
            if (seen == INVOKESTATIC) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (ARRAYS_ASLIST_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (Values.ONE.equals(itm.getConstant())) {
                            if (clsVersion >= MAJOR_1_8) {
                                bugReporter.reportBug(new BugInstance(this, BugType.LUI_USE_SINGLETON_LIST.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }

                            userValue = LUIUserValue.ONE_ITEM_LIST;
                        }
                    }
                } else if (COLLECTIONS_SINGLETONLIST_METHOD.equals(fqm)) {
                    userValue = LUIUserValue.ONE_ITEM_LIST;
                }
            } else if (seen == INVOKEINTERFACE) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (ADDALL_METHODS.contains(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if ((itm.getUserValue() == LUIUserValue.ONE_ITEM_LIST) && (itm.getRegisterNumber() < 0) && (itm.getXField() == null)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.LUI_USE_COLLECTION_ADD.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                } else if (LIST_STREAM_METHOD.equals(fqm)) {
                    userValue = LUIUserValue.LIST_STREAM;
                } else if (STREAM_FINDFIRST_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (itm.getUserValue() == LUIUserValue.LIST_STREAM) {
                            userValue = LUIUserValue.STREAM_OPTIONAL;
                        }
                    }
                }
            } else if (seen == INVOKEVIRTUAL) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (OPTIONAL_GET_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (itm.getUserValue() == LUIUserValue.STREAM_OPTIONAL) {
                            bugReporter.reportBug(
                                    new BugInstance(this, BugType.LUI_USE_GET0.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }
}
