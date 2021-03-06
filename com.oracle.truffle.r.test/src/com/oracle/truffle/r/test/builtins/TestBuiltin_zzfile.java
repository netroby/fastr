/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

public class TestBuiltin_zzfile extends TestBase {
    private static final String[] CTYPES = new String[]{"g", "b", "x"};

    @Test
    public void test1() {
        assertEval(TestBase.template("{ f <- tempfile(); c <- %0zfile(f); writeLines(as.character(1:100), c); close(c); readLines(f) }", CTYPES));
    }

    @Test
    public void test2() {
        assertEval(TestBase.template(
                        "{ f <- tempfile(); c <- %0zfile(f); writeLines(as.character(1:50), c); close(c); c <- %0zfile(f, \"a\"); writeLines(as.character(51:70), c); close(c); readLines(f) }",
                        CTYPES));
    }
}
