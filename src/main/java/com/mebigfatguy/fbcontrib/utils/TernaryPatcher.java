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

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.Const;

import edu.umd.cs.findbugs.OpcodeStack;

/**
 * restores OpcodeStack Item's userValues when a ternary is processed. This
 * class is required because Findbugs has a bug whereby it strips the user value
 * field from all OpcodeStack items when a GOTO is processed when items are on
 * the stack. Normally this is not the case, but in the case of ternary handling
 * there may be N items on the stack before what the ternary pushes. Now clearly
 * the uservalue should be stripped for items pushed on by both branches of the
 * ternary, but items that were on the stack before the ternary was executed
 * should be left alone. This is currently not happening in findbugs. So this
 * class saves off user values across a GOTO involved with a ternary and
 * restores them appropriately.
 */
public final class TernaryPatcher {

    private static List<Object> userValues = new ArrayList<Object>();
    private static boolean sawGOTO = false;

    private TernaryPatcher() {
    }

    /**
     * called before the execution of the parent OpcodeStack.sawOpcode() to save
     * user values if the opcode is a GOTO or GOTO_W.
     *
     * @param stack  the OpcodeStack with the items containing user values
     * @param opcode the opcode currently seen
     */
    public static void pre(OpcodeStack stack, int opcode) {
        if (sawGOTO) {
            return;
        }
        sawGOTO = (opcode == Const.GOTO) || (opcode == Const.GOTO_W);
        if (sawGOTO) {
            int depth = stack.getStackDepth();
            if (depth > 0) {
                userValues.clear();
                for (int i = 0; i < depth; i++) {
                    OpcodeStack.Item item = stack.getStackItem(i);
                    userValues.add(item.getUserValue());
                }
            }
        }
    }

    /**
     * called after the execution of the parent OpcodeStack.sawOpcode, to restore
     * the user values after the GOTO or GOTO_W's mergeJumps were processed
     *
     * @param stack  the OpcodeStack with the items containing user values
     * @param opcode the opcode currently seen
     */
    public static void post(OpcodeStack stack, int opcode) {
        if (!sawGOTO || (opcode == Const.GOTO) || (opcode == Const.GOTO_W)) {
            return;
        }
        int depth = stack.getStackDepth();
        for (int i = 0; i < depth && i < userValues.size(); i++) {
            OpcodeStack.Item item = stack.getStackItem(i);
            if (item.getUserValue() == null) {
                item.setUserValue(userValues.get(i));
            }
        }

        userValues.clear();
        sawGOTO = false;
    }
}
