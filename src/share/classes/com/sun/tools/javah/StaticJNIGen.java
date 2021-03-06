/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javah;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sun.tools.javah.TypeSignature.SignatureException;
import com.sun.tools.javah.Util.Exit;
import com.sun.tools.javah.staticjni.ArrayCallback;
import com.sun.tools.javah.staticjni.Callback;
import com.sun.tools.javah.staticjni.ExceptionCallback;
import com.sun.tools.javah.staticjni.FieldCallback;

/**
 * Header file generator for JNI.
 *
 * Supports only the creation of the body of the frontier.
 */
public abstract class StaticJNIGen extends JNI {
    StaticJNIGen(Util util, StaticJNIClassHelper helper) {
        super(util);
        this.helper = helper;
    }
    
    StaticJNIClassHelper helper;

    protected String staticjniType(TypeMirror t) throws Util.Exit {
    //	return staticjniType( t, null );
   // }
    //protected String staticjniType(TypeMirror t, Set<TypeMirror> usedTypes ) throws Util.Exit {
        TypeElement throwable = elems.getTypeElement("java.lang.Throwable");
        TypeElement jClass = elems.getTypeElement("java.lang.Class");
        TypeElement jString = elems.getTypeElement("java.lang.String");
        Element tclassDoc = types.asElement(t);

        switch (t.getKind()) {
            case ARRAY: {
                TypeMirror ct = ((ArrayType) t).getComponentType();
                switch (ct.getKind()) {
                    case BOOLEAN:  return "jbooleanArray";
                    case BYTE:     return "jbyteArray";
                    case CHAR:     return "jcharArray";
                    case SHORT:    return "jshortArray";
                    case INT:      return "jintArray";
                    case LONG:     return "jlongArray";
                    case FLOAT:    return "jfloatArray";
                    case DOUBLE:   return "jdoubleArray";
                    case ARRAY:
                    case DECLARED: return "jobjectArray";
                    default: throw new Error(ct.toString());
                }
            }

            case VOID:     return "void";
            case BOOLEAN:  return "jboolean";
            case BYTE:     return "jbyte";
            case CHAR:     return "jchar";
            case SHORT:    return "jshort";
            case INT:      return "jint";
            case LONG:     return "jlong";
            case FLOAT:    return "jfloat";
            case DOUBLE:   return "jdouble";

            case DECLARED: {
                if (tclassDoc.equals(jString))
                    return "jstring";
                else if (types.isAssignable(t, throwable.asType()))
                    return "jthrowable";
                else if (types.isAssignable(t, jClass.asType()))
                    return "jclass";
                else
                {
               // 	if ( usedTypes != null )
                //		usedTypes.add( t );
                    return types.asElement(t).getSimpleName().toString();
                }
            }
        }

        util.bug("jni.unknown.type");
        return null; /* dead code. */
    }

    protected String getCName(ExecutableElement md, TypeElement clazz,
            boolean longName) {
        return clazz.getSimpleName().toString() + "_" + md.getSimpleName();
        /*try {
            return mangler.mangleMethod(md, clazz, Mangle.Type.METHOD_JNI_SHORT)
                .substring(5);
        }
        catch ( SignatureException ex ) {
            return ""; // TODO when can this happen
        }
        /*
         * (longName) ? Mangle.Type.METHOD_JNI_LONG :
         * Mangle.Type.METHOD_JNI_SHORT);
         */
    }

    protected String getCName(VariableElement md, TypeElement clazz,
            boolean longName) {
        return clazz.getSimpleName().toString() + "_" + md.getSimpleName();
        /*
         * (longName) ? Mangle.Type.METHOD_JNI_LONG :
         * Mangle.Type.METHOD_JNI_SHORT);
         */
    }

    protected String getCImplName(ExecutableElement md, TypeElement clazz,
            boolean longName) throws SignatureException {
        return getCName(md, clazz, longName) + "__impl";
    }

    protected String castToStaticjni(TypeMirror arg, String name) {
    	if ( advancedStaticType( arg ) )
    		return "(" + types.asElement(arg).getSimpleName().toString() + ")" + name;
    	else
    		return name;
	}

    protected String castFromStaticjni(TypeMirror arg, String name) {
    	if ( advancedStaticType( arg ) )
    		return "(" + jniType(arg) +")" + name;
    	else
    		return name;
	}
    
    protected boolean	advancedStaticType( TypeMirror t ) {
        TypeElement throwable = elems.getTypeElement("java.lang.Throwable");
        TypeElement jClass = elems.getTypeElement("java.lang.Class");
        TypeElement jString = elems.getTypeElement("java.lang.String");
        Element tclassDoc = types.asElement(t);
        
		return t.getKind() == TypeKind.DECLARED &&
				!tclassDoc.equals(jString) && !types.isAssignable(t, throwable.asType()) &&
				!types.isAssignable(t, jClass.asType());
	}
    
    @Override
    public void write(OutputStream o, TypeElement clazz) throws Exit {
        helper.setCurrentClass(clazz);
    }
    
    String normalSignature( Callback callback )
    {
        ExecutableElement m = callback.meth;
        
        // TODO add conflict detection
        
        // return
        String r = staticjniType(m.getReturnType()) + " ";
        
        // fun name
        r += getCName( m, callback.recvType, false ) + "( ";
        
        // self
        ArrayList<String> args = new ArrayList<String>();
        if (!m.getModifiers().contains(Modifier.STATIC))
            args.add( staticjniType(callback.recvType.asType()) + " self" );

        // params
        List<? extends VariableElement> paramargs = m.getParameters();
        for (VariableElement p: paramargs) {
            TypeMirror arg = types.erasure(p.asType());
            args.add( staticjniType(arg) + " " + p.getSimpleName() );
        }
        
        if ( args.size() > 0 )
            r += args.get(0);
        if ( args.size() > 1 )
            for ( String s: args.subList(1, args.size()) )
                r += ", " + s;
            
        r += " )";
        return r;
    }
    
    String superSignature( Callback callback )
    {
        ExecutableElement m = callback.meth;
        
        String r = staticjniType(m.getReturnType()) + " ";
        
        // fun name
        r += "super_" + getCName( m, callback.recvType, false ) + "( ";
        
        // self
        ArrayList<String> args = new ArrayList<String>();
        if (!m.getModifiers().contains(Modifier.STATIC))
            args.add( staticjniType(callback.recvType.asType()) + " self" );

        // params
        List<? extends VariableElement> paramargs = m.getParameters();
        for (VariableElement p: paramargs) {
            TypeMirror arg = types.erasure(p.asType());
            args.add( staticjniType(arg) + " " + p.getSimpleName() );
        }
        
        if ( args.size() > 0 )
            r += args.get(0);
        if ( args.size() > 1 )
            for ( String s: args.subList(1, args.size()) )
                r += ", " + s;
            
        r += " )";
        return r;
    }

    String fieldGetterSignature( FieldCallback callback )
    {
        String baseName = getCName( callback.field, callback.recvType, false );
        String fieldStaticjniType = staticjniType(callback.field.asType());
        String receiverStaticJNIjniType = staticjniType(callback.recvType.asType());
        // TODO static field if (!c.field.getModifiers().contains(Modifier.STATIC)

        if (callback.field.getModifiers().contains(Modifier.STATIC))
        	return fieldStaticjniType + " get_" + baseName + "( )";
        else
           	return fieldStaticjniType + " get_" + baseName + "( " + receiverStaticJNIjniType + " self )";
    }

    String fieldSetterSignature( FieldCallback callback )
    {
        String baseName = getCName( callback.field, callback.recvType, false );
        String fieldStaticjniType = staticjniType(callback.field.asType());
        String receiverStaticJNIjniType = staticjniType(callback.recvType.asType());
        // TODO static field if (!c.field.getModifiers().contains(Modifier.STATIC)
        
        // setter
        if (callback.field.getModifiers().contains(Modifier.STATIC))
        	return "void set_" + baseName + "( " + fieldStaticjniType + " in_value )";
        else
        	return "void set_" + baseName + "( " + receiverStaticJNIjniType + " self, " + fieldStaticjniType + " in_value )";
    }

    String constructorSignature( Callback callback )
    {
        ExecutableElement m = callback.meth;
        
        String r = staticjniType(callback.recvType.asType()) + " ";
        
        // fun name
        r += "new_" + callback.recvType.getSimpleName() + "( ";
        
        // params
        ArrayList<String> args = new ArrayList<String>();
        List<? extends VariableElement> paramargs = m.getParameters();
        for (VariableElement p: paramargs) {
            TypeMirror arg = types.erasure(p.asType());
            args.add( staticjniType(arg) + " " + p.getSimpleName() );
        }
        
        if ( args.size() > 0 )
            r += args.get(0);
        if ( args.size() > 1 )
            for ( String s: args.subList(1, args.size()) )
                r += ", " + s;
            
        r += " )";
        return r;
    }
    
    String constructorSignature(ExecutableElement e) { 
        StringBuffer sb = new StringBuffer("(");
        String sep = "";
        for (VariableElement p : e.getParameters()) {
            sb.append(sep);
            sb.append(types.erasure(p.asType()).toString());
            sep = ",";
        }
        sb.append(")");
        return sb.toString();
    }
    
    String throwSignature(ExceptionCallback e, TypeElement from, boolean full) {
    	TypeElement t = (TypeElement)types.asElement(e.exceptionType);
        StringBuffer sb = new StringBuffer();
        if ( full  ) sb.append( "void " );
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "throw_new_"+t.getSimpleName() );
        if ( full  ) sb.append( "( const char* msg )" );
        return sb.toString();
    }
    
    /* Arrays */
    
    String accessArrayStructureMacro(ArrayCallback c, boolean critical) {
        StringBuffer sb = new StringBuffer();
        if ( critical ) sb.append( "critical_" );
        sb.append( "access_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String accessArrayGet(ArrayCallback c, TypeElement from, boolean critical) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "get_");
        if ( critical ) sb.append( "critical_" );
        sb.append( "access_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String accessArrayRelease(ArrayCallback c, TypeElement from, boolean critical) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "release_");
        if ( critical ) sb.append( "critical_" );
        sb.append( "access_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String accessArrayLength(ArrayCallback c, TypeElement from ) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "get_length_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String newArray(ArrayCallback c, TypeElement from ) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "new_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String setArrayRegion(ArrayCallback c, TypeElement from ) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "set_region_");
        sb.append( staticjniType( c.arrayType ) );
    	return sb.toString();
    }
    
    String accessStringStructureMacro() {
        StringBuffer sb = new StringBuffer();
        sb.append( "access_jstring");
    	return sb.toString();
    }
    
    String accessStringGet(TypeElement from) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "get_jstring");
    	return sb.toString();
    }
    
    String accessStringRelease(TypeElement from) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "release_jstring");
    	return sb.toString();
    }
    
    String accessStringLength(TypeElement from) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "get_length_jstring");
    	return sb.toString();
    }
    
    String newString(TypeElement from) {
        StringBuffer sb = new StringBuffer();
        if ( from != null ) sb.append( from.getSimpleName() + "_" );
        sb.append( "new_jstring");
    	return sb.toString();
    }
}
