/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.ffi.impl.llvm;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.ffi.impl.common.LibPaths;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ContextState;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.NativeFunction;
import com.oracle.truffle.r.runtime.ffi.RFFIContext;

/**
 * A facade for the context state for the Truffle LLVM factory. Delegates to the various
 * module-specific pieces of state.
 */
final class TruffleLLVM_Context extends RFFIContext {

    final TruffleLLVM_DLL.ContextStateImpl dllState = new TruffleLLVM_DLL.ContextStateImpl();
    final TruffleLLVM_Call.ContextStateImpl callState = new TruffleLLVM_Call.ContextStateImpl();

    TruffleLLVM_Context() {
        super(new TruffleLLVM_C(), new TruffleLLVM_Base(), new TruffleLLVM_Call(), new TruffleLLVM_DLL(), new TruffleLLVM_UserRng(), new TruffleLLVM_Zip(), new TruffleLLVM_PCRE(),
                        new TruffleLLVM_Lapack(), new TruffleLLVM_Stats(),
                        new TruffleLLVM_Tools(), new TruffleLLVM_REmbed(), new TruffleLLVM_Misc());
    }

    static TruffleLLVM_Context getContextState() {
        return (TruffleLLVM_Context) RContext.getInstance().getStateRFFI();
    }

    static TruffleLLVM_Context getContextState(RContext context) {
        return (TruffleLLVM_Context) context.getStateRFFI();
    }

    @Override
    public ContextState initialize(RContext context) {
        if (context.isInitial()) {
            String librffiPath = LibPaths.getBuiltinLibPath("R");
            DLL.loadLibR(context, librffiPath);
        }
        dllState.initialize(context);
        callState.initialize(context);
        return this;
    }

    @Override
    public void initializeVariables(RContext context) {
        super.initializeVariables(context);

        callState.initializeVariables();
        ((TruffleLLVM_PCRE) pcreRFFI).initialize();
        ((TruffleLLVM_Lapack) lapackRFFI).initialize();
        ((TruffleLLVM_Zip) zipRFFI).initialize();
    }

    @Override
    public void beforeDispose(RContext context) {
        dllState.beforeDispose(context);
        callState.beforeDispose(context);
    }

    @Override
    public TruffleObject lookupNativeFunction(NativeFunction function) {
        Object symValue = RContext.getInstance().getEnv().importSymbol("@" + function.getCallName());
        return (TruffleObject) symValue;
    }
}