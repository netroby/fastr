/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.util.Iterator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.attributes.GetAttributeNode;
import com.oracle.truffle.r.nodes.attributes.IterableAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetRowNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributesLayout;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RInteropScalar;
import com.oracle.truffle.r.runtime.data.RListBase;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.IdenticalVisitor;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * Internal part of {@code identical}. The default values for args after {@code x} and {@code y} are
 * all default to {@code TRUE/FALSE} in the R wrapper.
 *
 * TODO Implement the full set of types. This will require refactoring the code so that a generic
 * "identical" function can be called recursively to handle lists and language objects (and
 * closures) GnuR compares attributes also. The general case is therefore slow but the fast path
 * needs to be fast! The five defaulted logical arguments are supposed to be cast to logical and
 * checked for NA (regardless of whether they are used).
 *
 * TODO implement {@code ignoreSrcref}.
 *
 * N.B. GNU R allows all the {@code ignoreXXX} options to be optional. However, the only call to the
 * {@code .Internal} in the GNU R code base is from the closure wrapper, which passes all of them.
 * There may be some package that calls the {@code .Internal} directly with less, however.
 */
@RBuiltin(name = "identical", kind = INTERNAL, parameterNames = {"x", "y", "num.eq", "single.NA", "attrib.as.set", "ignore.bytecode", "ignore.environment", "ignore.srcref"}, behavior = PURE)
public abstract class Identical extends RBuiltinNode.Arg8 {

    protected abstract byte executeByte(Object x, Object y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref);

    @Child private Identical identicalRecursive;
    @Child private Identical identicalRecursiveAttr;
    @Child private IterableAttributeNode attrIterNodeX = IterableAttributeNode.create();
    @Child private IterableAttributeNode attrIterNodeY = IterableAttributeNode.create();

    static {
        Casts casts = new Casts(Identical.class);
        casts.arg("num.eq").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
        casts.arg("single.NA").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
        casts.arg("attrib.as.set").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
        casts.arg("ignore.bytecode").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
        casts.arg("ignore.environment").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
        casts.arg("ignore.srcref").asLogicalVector().findFirst(RRuntime.LOGICAL_FALSE).map(toBoolean());
    }

    private final ConditionProfile vecLengthProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile differentTypesProfile = ConditionProfile.createBinaryProfile();

    // Note: the execution of the recursive cases is not done directly and not through RCallNode or
    // similar, this means that the visibility handling is left to us.

    private byte identicalRecursive(Object x, Object y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (identicalRecursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            identicalRecursive = insert(IdenticalNodeGen.create());
        }
        return identicalRecursive.executeByte(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    private byte identicalRecursiveAttr(String attrName, Object xx, Object yy, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        if (identicalRecursiveAttr == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            identicalRecursiveAttr = insert(IdenticalNodeGen.create());
        }
        Object x = xx;
        Object y = yy;
        if (GetAttributeNode.isRowNamesAttr(attrName)) {
            x = GetRowNamesAttributeNode.convertRowNamesToSeq(x);
            y = GetRowNamesAttributeNode.convertRowNamesToSeq(y);
        }
        return identicalRecursiveAttr.executeByte(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isRNull(x) || isRNull(y)")
    protected byte doInternalIdenticalNull(Object x, Object y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isRMissing(x) || isRMissing(y)")
    protected byte doInternalIdenticalMissing(Object x, Object y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdentical(byte x, byte y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdentical(String x, String y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        return RRuntime.asLogical(x.equals(y));
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdentical(double x, double y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (singleNA) {
            if (RRuntime.isNA(x)) {
                return RRuntime.asLogical(RRuntime.isNA(y));
            } else if (RRuntime.isNA(y)) {
                return RRuntime.LOGICAL_FALSE;
            } else if (Double.isNaN(x)) {
                return RRuntime.asLogical(Double.isNaN(y));
            } else if (Double.isNaN(y)) {
                return RRuntime.LOGICAL_FALSE;
            }
        }
        if (numEq) {
            if (!singleNA) {
                if (Double.isNaN(x) || Double.isNaN(y)) {
                    return RRuntime.asLogical(Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y));
                }
            }
            return RRuntime.asLogical(x == y);
        }
        return RRuntime.asLogical(Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y));
    }

    private byte identicalAttr(RAttributable x, RAttributable y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        DynamicObject xAttributes = x.getAttributes();
        DynamicObject yAttributes = y.getAttributes();
        int xSize = xAttributes == null ? 0 : xAttributes.size();
        int ySize = yAttributes == null ? 0 : yAttributes.size();
        if (xSize == 0 && ySize == 0) {
            return RRuntime.LOGICAL_TRUE;
        } else if (xSize != ySize) {
            return RRuntime.LOGICAL_FALSE;
        } else {
            return identicalAttrInternal(numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref, xAttributes, yAttributes);
        }
    }

    @TruffleBoundary
    private byte identicalAttrInternal(boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref, DynamicObject xAttributes,
                    DynamicObject yAttributes) {
        if (attribAsSet) {
            // make sure all attributes from x are in y, with identical values
            Iterator<RAttributesLayout.RAttribute> xIter = attrIterNodeX.execute(xAttributes).iterator();
            while (xIter.hasNext()) {
                RAttributesLayout.RAttribute xAttr = xIter.next();
                Object yValue = yAttributes.get(xAttr.getName());
                if (yValue == null) {
                    return RRuntime.LOGICAL_FALSE;
                }
                byte res = identicalRecursiveAttr(xAttr.getName(), xAttr.getValue(), yValue, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
                if (res == RRuntime.LOGICAL_FALSE) {
                    return RRuntime.LOGICAL_FALSE;
                }
            }
            // make sure all attributes from y are in x
            Iterator<RAttributesLayout.RAttribute> yIter = attrIterNodeY.execute(yAttributes).iterator();
            while (xIter.hasNext()) {
                RAttributesLayout.RAttribute yAttr = yIter.next();
                if (!xAttributes.containsKey(yAttr.getName())) {
                    return RRuntime.LOGICAL_FALSE;
                }
            }
        } else {
            Iterator<RAttributesLayout.RAttribute> xIter = attrIterNodeX.execute(xAttributes).iterator();
            Iterator<RAttributesLayout.RAttribute> yIter = attrIterNodeY.execute(yAttributes).iterator();
            while (xIter.hasNext()) {
                RAttributesLayout.RAttribute xAttr = xIter.next();
                RAttributesLayout.RAttribute yAttr = yIter.next();
                if (!xAttr.getName().equals(yAttr.getName())) {
                    return RRuntime.LOGICAL_FALSE;
                }
                byte res = identicalRecursiveAttr(xAttr.getName(), xAttr.getValue(), yAttr.getValue(), numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
                if (res == RRuntime.LOGICAL_FALSE) {
                    return RRuntime.LOGICAL_FALSE;
                }
            }
        }
        return RRuntime.LOGICAL_TRUE;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdentical(REnvironment x, REnvironment y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        // reference equality for environments
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdentical(RSymbol x, RSymbol y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @Specialization
    byte doInternalIdentical(RFunction x, RFunction y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (x == y) {
            // trivial case
            return RRuntime.LOGICAL_TRUE;
        } else {
            return doInternalIdenticalSlowpath(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
        }
    }

    @TruffleBoundary
    private byte doInternalIdenticalSlowpath(RFunction x, RFunction y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        boolean xb = x.isBuiltin();
        boolean yb = y.isBuiltin();
        if ((xb && !yb) || (yb && !xb)) {
            return RRuntime.LOGICAL_FALSE;
        }

        if (xb && yb) {
            // equal if the factories are
            return RRuntime.asLogical(x.getRBuiltin() == y.getRBuiltin());
        }

        // compare the structure
        if (!new IdenticalVisitor().accept((RSyntaxNode) x.getRootNode(), (RSyntaxNode) y.getRootNode())) {
            return RRuntime.LOGICAL_FALSE;
        }
        // The environments have to match unless ignoreEnvironment == false
        if (!ignoreEnvironment) {
            if (x.getEnclosingFrame() != y.getEnclosingFrame()) {
                return RRuntime.LOGICAL_FALSE;
            }
        }
        // finally check attributes
        return identicalAttr(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    @Specialization(guards = "!vectorsLists(x, y)")
    protected byte doInternalIdenticalGeneric(RAbstractVector x, RAbstractVector y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        if (vecLengthProfile.profile(x.getLength() != y.getLength()) || differentTypesProfile.profile(x.getRType() != y.getRType())) {
            return RRuntime.LOGICAL_FALSE;
        } else {
            for (int i = 0; i < x.getLength(); i++) {
                if (!x.getDataAtAsObject(i).equals(y.getDataAtAsObject(i))) {
                    return RRuntime.LOGICAL_FALSE;
                }
            }
        }
        return identicalAttr(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    @Specialization
    protected byte doInternalIdenticalGeneric(RListBase x, RListBase y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (x.getLength() != y.getLength()) {
            return RRuntime.LOGICAL_FALSE;
        }
        for (int i = 0; i < x.getLength(); i++) {
            byte res = identicalRecursive(x.getDataAt(i), y.getDataAt(i), numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
            if (res == RRuntime.LOGICAL_FALSE) {
                return RRuntime.LOGICAL_FALSE;
            }
        }
        return identicalAttr(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    @Specialization
    protected byte doInternalIdenticalGeneric(RS4Object x, RS4Object y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (x.isS4() != y.isS4()) {
            return RRuntime.LOGICAL_FALSE;
        }
        return identicalAttr(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdenticalGeneric(RExternalPtr x, RExternalPtr y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        return RRuntime.asLogical(x.getAddr() == y.getAddr());
    }

    @Specialization
    @TruffleBoundary
    protected byte doInternalIdenticalGeneric(RPairList x, RPairList y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment, boolean ignoreSrcref) {
        if (x == y) {
            return RRuntime.LOGICAL_TRUE;
        }
        boolean xHasClosure = x.hasClosure();
        boolean yHasClosure = y.hasClosure();
        try {
            if (identicalRecursive(x.car(), y.car(), numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref) == RRuntime.LOGICAL_FALSE) {
                return RRuntime.LOGICAL_FALSE;
            }
            Object tmpXCdr = x.cdr();
            Object tmpYCdr = y.cdr();
            while (true) {
                if (RPairList.isNull(tmpXCdr) && RPairList.isNull(tmpYCdr)) {
                    break;
                } else if (RPairList.isNull(tmpXCdr) || RPairList.isNull(tmpYCdr)) {
                    return RRuntime.LOGICAL_FALSE;
                } else {
                    RPairList xSubList = (RPairList) tmpXCdr;
                    RPairList ySubList = (RPairList) tmpYCdr;
                    Object tagX = xSubList.getTag();
                    if (tagX instanceof RSymbol && "".equals(((RSymbol) tagX).getName())) {
                        tagX = RNull.instance;
                    }
                    Object tagY = ySubList.getTag();
                    if (tagY instanceof RSymbol && "".equals(((RSymbol) tagY).getName())) {
                        tagY = RNull.instance;
                    }

                    if (RPairList.isNull(tagX) && RPairList.isNull(tagY)) {
                        // continue
                    } else if (RPairList.isNull(tagX) || RPairList.isNull(tagY)) {
                        return RRuntime.LOGICAL_FALSE;
                    } else {
                        if (tagY instanceof RSymbol && tagY instanceof RSymbol) {
                            if (xSubList.getTag() != ySubList.getTag()) {
                                return RRuntime.LOGICAL_FALSE;
                            }
                        } else {
                            throw RInternalError.unimplemented("non-RNull and non-RSymbol pairlist tags are not currently supported");
                        }
                    }
                    if (identicalRecursive(xSubList.car(), ySubList.car(), numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref) == RRuntime.LOGICAL_FALSE) {
                        return RRuntime.LOGICAL_FALSE;
                    }
                    if (xSubList.getAttributes() != null || ySubList.getAttributes() != null) {
                        throw RInternalError.unimplemented("attributes of internal pairlists are not currently supported");
                    }
                    tmpXCdr = ((RPairList) tmpXCdr).cdr();
                    tmpYCdr = ((RPairList) tmpYCdr).cdr();
                }
            }
            return identicalAttr(x, y, numEq, singleNA, attribAsSet, ignoreBytecode, ignoreEnvironment, ignoreSrcref);
        } finally {
            // if they were closures before, they can still be afterwards
            if (xHasClosure) {
                x.allowClosure();
            }
            if (yHasClosure) {
                y.allowClosure();
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdenticalForeignObject(RInteropScalar x, RInteropScalar y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "areForeignObjects(x, y)")
    protected byte doInternalIdenticalForeignObject(TruffleObject x, TruffleObject y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected byte doInternalIdenticalForeignObject(RPromise x, RPromise y, boolean numEq, boolean singleNA, boolean attribAsSet, boolean ignoreBytecode, boolean ignoreEnvironment,
                    boolean ignoreSrcref) {
        return RRuntime.asLogical(x == y);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected byte doInternalIdenticalWrongTypes(Object x, Object y, Object numEq, Object singleNA, Object attribAsSet, Object ignoreBytecode, Object ignoreEnvironment, Object ignoreSrcref) {
        assert x.getClass() != y.getClass();
        return RRuntime.LOGICAL_FALSE;
    }

    protected boolean areForeignObjects(TruffleObject x, TruffleObject y) {
        return RRuntime.isForeignObject(x) && RRuntime.isForeignObject(y);
    }

    protected boolean vectorsLists(RAbstractVector x, RAbstractVector y) {
        return x instanceof RListBase && y instanceof RListBase;
    }

    public static Identical create() {
        return IdenticalNodeGen.create();
    }
}
