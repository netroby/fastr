/*
 * Copyright (C) 2001-3 Paul Murrell
 * Copyright (c) 1998-2013, The R Core Team
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.library.fastrGrid;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * Gets called when the grid package is loaded.
 */
public abstract class LInitGrid extends RExternalBuiltinNode.Arg1 {
    static {
        Casts casts = new Casts(LInitGrid.class);
        casts.arg(0).mustBe(REnvironment.class);
    }

    public static LInitGrid create() {
        return LInitGridNodeGen.create();
    }

    @Specialization
    @TruffleBoundary
    public Object doEnv(REnvironment gridEnv) {
        GridContext context = GridContext.getContext();
        context.getGridState().init(gridEnv);
        return RNull.instance;
    }
}
