/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;

public final class RExpression extends RListBase implements RAbstractVector {

    RExpression(Object[] data) {
        super(data);
    }

    RExpression(Object[] data, int[] dims, RStringVector names, RList dimNames) {
        super(data, dims, names, dimNames);
    }

    @Override
    public RType getRType() {
        return RType.Expression;
    }

    @Override
    public RVector<?> materialize() {
        return this;
    }

    @Override
    @TruffleBoundary
    protected RExpression internalCopy() {
        return new RExpression(Arrays.copyOf(data, data.length), getDimensions(), null, null);
    }

    @Override
    @TruffleBoundary
    protected RExpression internalDeepCopy() {
        // TOOD: only used for nested list updates, but still could be made faster (through a
        // separate AST node?)
        RExpression listCopy = new RExpression(Arrays.copyOf(data, data.length), getDimensions(), null, null);
        for (int i = 0; i < listCopy.getLength(); i++) {
            Object el = listCopy.getDataAt(i);
            if (el instanceof RVector) {
                Object elCopy = ((RVector<?>) el).deepCopy();
                listCopy.updateDataAt(i, elCopy, null);
            }
        }
        return listCopy;
    }

    @Override
    public RExpression createEmptySameType(int newLength, boolean newIsComplete) {
        return RDataFactory.createExpression(newLength);
    }

    @Override
    public RExpression copyWithNewDimensions(int[] newDimensions) {
        return RDataFactory.createExpression(data, newDimensions);
    }

    @Override
    protected RExpression internalCopyResized(int size, boolean fillNA, int[] dimensions) {
        return RDataFactory.createExpression(copyResizedData(size, fillNA), dimensions);
    }

    private static final class FastPathAccess extends FastPathFromListAccess {

        FastPathAccess(RAbstractContainer value) {
            super(value);
        }

        @Override
        public RType getType() {
            return RType.Expression;
        }

        @Override
        protected Object getListElementImpl(AccessIterator accessIter, int index) {
            return ((Object[]) accessIter.getStore())[index];
        }

        @Override
        protected void setListElementImpl(AccessIterator accessIter, int index, Object value) {
            ((Object[]) accessIter.getStore())[index] = value;
        }
    }

    @Override
    public VectorAccess access() {
        return new FastPathAccess(this);
    }

    private static final SlowPathFromListAccess SLOW_PATH_ACCESS = new SlowPathFromListAccess() {
        @Override
        public RType getType() {
            return RType.Expression;
        }

        @Override
        protected Object getListElementImpl(AccessIterator accessIter, int index) {
            return ((RExpression) accessIter.getStore()).data[index];
        }

        @Override
        protected void setListElementImpl(AccessIterator accessIter, int index, Object value) {
            ((RExpression) accessIter.getStore()).data[index] = value;
        }
    };

    @Override
    public VectorAccess slowPathAccess() {
        return SLOW_PATH_ACCESS;
    }
}
