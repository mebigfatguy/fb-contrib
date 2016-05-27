package com.mebigfatguy.fbcontrib.detect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for two or more try catch blocks that are consecutive and catch the same kind of exception, and throw the same exception always. These blocks can be
 * coalesced into one.
 */

@CustomUserValue
public class StackedTryBlocks extends BytecodeScanningDetector {

    private static JavaClass THROWABLE_CLASS;

    static {
        try {
            THROWABLE_CLASS = Repository.lookupClass("java/lang/Throwable");
        } catch (ClassNotFoundException cnfe) {
            THROWABLE_CLASS = null;
        }
    }

    private final BugReporter bugReporter;
    private List<TryBlock> blocks;
    private List<TryBlock> inBlocks;
    private List<Integer> transitionPoints;
    private OpcodeStack stack;

    public StackedTryBlocks(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the opcode stack
     *
     * @param classContext
     *            the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            if (THROWABLE_CLASS != null) {
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to look for 'idea' try catch blocks to find issues specifically, method needs two or more try catch blocks that only catch one
     * exception type.
     *
     * @param obj
     *            the currently parsed code object
     */
    @Override
    public void visitCode(Code obj) {

        try {
            XMethod xMethod = getXMethod();
            if (xMethod != null) {
                String[] tes = xMethod.getThrownExceptions();
                Set<String> thrownExceptions = new HashSet<String>(Arrays.<String> asList((tes == null) ? new String[0] : tes));

                blocks = new ArrayList<TryBlock>();
                inBlocks = new ArrayList<TryBlock>();
                transitionPoints = new ArrayList<Integer>();

                CodeException[] ces = obj.getExceptionTable();
                for (CodeException ce : ces) {
                    TryBlock tb = new TryBlock(ce);
                    int existingBlock = blocks.indexOf(tb);
                    if (existingBlock >= 0) {
                        tb = blocks.get(existingBlock);
                        tb.addCatchType(ce);
                    } else {
                        blocks.add(tb);
                    }
                }

                Iterator<TryBlock> it = blocks.iterator();
                while (it.hasNext()) {
                    TryBlock block = it.next();
                    if (block.hasMultipleHandlers() || block.isFinally() || block.catchIsThrown(getConstantPool(), thrownExceptions)) {
                        it.remove();
                    }
                }

                if (blocks.size() > 1) {
                    stack.resetForMethodEntry(this);
                    super.visitCode(obj);

                    if (blocks.size() > 1) {
                        Collections.sort(transitionPoints);

                        TryBlock firstBlock = blocks.get(0);
                        for (int i = 1; i < blocks.size(); i++) {
                            TryBlock secondBlock = blocks.get(i);

                            if (!blocksSplitAcrossTransitions(firstBlock, secondBlock) && (firstBlock.getCatchType() == secondBlock.getCatchType())
                                    && firstBlock.getThrowSignature().equals(secondBlock.getThrowSignature())
                                    && firstBlock.getMessage().equals(secondBlock.getMessage())
                                    && firstBlock.getExceptionSignature().equals(secondBlock.getExceptionSignature())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.STB_STACKED_TRY_BLOCKS.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLineRange(this, firstBlock.getStartPC(), firstBlock.getEndHandlerPC())
                                        .addSourceLineRange(this, secondBlock.getStartPC(), secondBlock.getEndHandlerPC()));
                            }

                            firstBlock = secondBlock;
                        }
                    }
                }
            }
        } finally {
            blocks = null;
            inBlocks = null;
            transitionPoints = null;
        }
    }

    /**
     * overrides the visitor to document what catch blocks do with regard to rethrowing the exceptions, and if the message is a static message
     *
     * @param seen
     *            the currently parsed opcode
     */

    @Override
    public void sawOpcode(int seen) {

        String message = null;
        try {
            stack.precomputation(this);

            if ((seen == TABLESWITCH) || (seen == LOOKUPSWITCH)) {
                for (int offset : getSwitchOffsets()) {
                    transitionPoints.add(Integer.valueOf(offset));
                }
            } else if (isBranch(seen) && (getBranchOffset() < 0)) {
                // throw out try blocks in loops, this could cause false
                // negatives
                // with two try/catches in one loop, but more unlikely
                Iterator<TryBlock> it = blocks.iterator();
                int target = getBranchTarget();
                while (it.hasNext()) {
                    TryBlock block = it.next();
                    if (block.getStartPC() >= target) {
                        it.remove();
                    }
                }
            }

            int pc = getPC();
            TryBlock block = findBlockWithStart(pc);
            if (block != null) {
                inBlocks.add(block);
                block.setState(TryBlock.State.IN_TRY);
            }

            if (inBlocks.size() > 0) {
                TryBlock innerBlock = inBlocks.get(inBlocks.size() - 1);

                int nextPC = getNextPC();
                if (innerBlock.atHandlerPC(nextPC)) {
                    if ((seen == GOTO) || (seen == GOTO_W)) {
                        innerBlock.setEndHandlerPC(getBranchTarget());
                    } else {
                        inBlocks.remove(innerBlock);
                        blocks.remove(innerBlock);
                    }
                } else if (innerBlock.atHandlerPC(pc)) {
                    innerBlock.setState(TryBlock.State.IN_CATCH);
                } else if (innerBlock.atEndHandlerPC(pc)) {
                    inBlocks.remove(inBlocks.size() - 1);
                    innerBlock.setState(TryBlock.State.AFTER);
                }

                if (innerBlock.inCatch()) {
                    if (((seen >= Constants.IFEQ) && ((seen <= Constants.RET))) || ((seen >= Constants.IRETURN) && (seen <= Constants.RETURN))
                            || (seen == GOTO_W)) {
                        blocks.remove(innerBlock);
                        inBlocks.remove(inBlocks.size() - 1);
                    } else if (seen == ATHROW) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            XMethod xm = item.getReturnValueOf();
                            if (xm != null) {
                                innerBlock.setThrowSignature(xm.getSignature());
                            }
                            innerBlock.setExceptionSignature(item.getSignature());
                            innerBlock.setMessage((String) item.getUserValue());
                        } else {
                            inBlocks.remove(inBlocks.size() - 1);
                            innerBlock.setState(TryBlock.State.AFTER);
                        }
                    } else if ((seen == INVOKESPECIAL) && Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                        String cls = getClassConstantOperand();
                        JavaClass exCls = Repository.lookupClass(cls);
                        if (exCls.instanceOf(THROWABLE_CLASS)) {
                            String signature = getSigConstantOperand();
                            Type[] types = Type.getArgumentTypes(signature);
                            if (types.length > 0) {
                                if ("Ljava/lang/String;".equals(types[0].getSignature()) && (stack.getStackDepth() >= types.length)) {
                                    OpcodeStack.Item item = stack.getStackItem(types.length - 1);
                                    message = (String) item.getConstant();
                                    if (message == null) {
                                        message = "____UNKNOWN____" + System.identityHashCode(item);
                                    }
                                }
                            } else {
                                message = "";
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
            if ((message != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(message);
            }
        }
    }

    /**
     * looks for an existing try block that has this pc as a start of the try
     *
     * @param pc
     *            the current program counter
     * @return the tryblock if this statement starts it, else null
     */
    private TryBlock findBlockWithStart(int pc) {

        for (TryBlock block : blocks) {
            if (block.atStartPC(pc)) {
                return block;
            }
        }

        return null;
    }

    private boolean blocksSplitAcrossTransitions(TryBlock firstBlock, TryBlock secondBlock) {
        if (!transitionPoints.isEmpty()) {
            Iterator<Integer> it = transitionPoints.iterator();
            while (it.hasNext()) {
                Integer transitionPoint = it.next();
                if (transitionPoint.intValue() < firstBlock.handlerPC) {
                    it.remove();
                } else {
                    return transitionPoint.intValue() < secondBlock.handlerPC;
                }
            }
        }

        return false;
    }

    static class TryBlock {

        enum State {
            BEFORE, IN_TRY, IN_CATCH, AFTER
        };

        private int startPC;
        private int endPC;
        private int handlerPC;
        private int endHandlerPC;
        private BitSet catchTypes;
        private String exSig;
        private String throwSig;
        private String message;
        private State state;

        TryBlock(CodeException ce) {
            startPC = ce.getStartPC();
            endPC = ce.getEndPC();
            handlerPC = ce.getHandlerPC();
            endHandlerPC = -1;
            catchTypes = new BitSet();
            catchTypes.set(ce.getCatchType());
            state = State.BEFORE;
        }

        void addCatchType(CodeException ce) {
            catchTypes.set(ce.getCatchType());
        }

        void setState(State executionState) {
            state = executionState;
        }

        boolean inCatch() {
            return state == State.IN_CATCH;
        }

        boolean hasMultipleHandlers() {
            int bit = catchTypes.nextSetBit(0);
            return catchTypes.nextSetBit(bit + 1) >= 0;
        }

        boolean isFinally() {
            return catchTypes.get(0);
        }

        boolean catchIsThrown(ConstantPool pool, Set<String> thrownExceptions) {
            if (thrownExceptions.size() == 0) {
                return false;
            }

            int exIndex = catchTypes.nextSetBit(0);
            String exName = ((ConstantClass) pool.getConstant(exIndex)).getBytes(pool);
            return thrownExceptions.contains(exName);
        }

        void setEndHandlerPC(int end) {
            endHandlerPC = end;
        }

        void setExceptionSignature(String sig) {
            exSig = sig;
        }

        void setThrowSignature(String sig) {
            throwSig = sig;
        }

        void setMessage(String m) {
            message = m;
        }

        String getExceptionSignature() {
            return (exSig == null) ? String.valueOf(System.identityHashCode(this)) : exSig;
        }

        String getThrowSignature() {
            return (throwSig == null) ? String.valueOf(System.identityHashCode(this)) : throwSig;
        }

        String getMessage() {
            return (message == null) ? String.valueOf(System.identityHashCode(this)) : message;
        }

        int getStartPC() {
            return startPC;
        }

        int getEndHandlerPC() {
            return endHandlerPC;
        }

        boolean atStartPC(int pc) {
            return startPC == pc;
        }

        boolean atHandlerPC(int pc) {
            return handlerPC == pc;
        }

        boolean atEndHandlerPC(int pc) {
            return (endHandlerPC >= 0) && (endHandlerPC == pc);
        }

        int getCatchType() {
            return catchTypes.nextSetBit(0);
        }

        @Override
        public int hashCode() {
            return startPC ^ endPC;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TryBlock)) {
                return false;
            }

            TryBlock that = (TryBlock) o;
            return (startPC == that.startPC) && (endPC == that.endPC);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
