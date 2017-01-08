/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
package com.mebigfatguy.fbcontrib;

import javax.swing.JOptionPane;

/**
 * a simple main app that gives information. This is the jar double-click class.
 * Not used under normal situations.
 */
public final class FBContrib {

    private FBContrib() {}

    /**
     * shows the simple help
     *
     * @param args
     *            standard command line args
     */
    public static void main(final String[] args) {
        JOptionPane.showMessageDialog(null,
                "To use fb-contrib, copy this jar file into your local FindBugs plugin directory, and use FindBugs as usual.\n\nfb-contrib is a trademark of MeBigFatGuy.com\nFindBugs is a trademark of the University of Maryland",
                "fb-contrib: copyright 2005-2017", JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);
    }
}
