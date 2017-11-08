/*
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package mockit.external.asm;

import java.util.*;

import mockit.internal.util.*;
import static mockit.external.asm.Opcodes.*;
import static mockit.internal.util.ClassLoad.OBJECT;

/**
 * A {@link ClassVisitor} that generates classes in bytecode form. More precisely this visitor generates a byte array
 * conforming to the Java class file format. It can be used alone, to generate a Java class "from scratch", or with one
 * or more {@link ClassReader} and adapter class visitor to generate a modified class from one or more existing Java
 * classes.
 *
 * @author Eric Bruneton
 */
public final class ClassWriter extends ClassVisitor
{
   /**
    * Pseudo access flag to distinguish between the synthetic attribute and the synthetic access flag.
    */
   static final int ACC_SYNTHETIC_ATTRIBUTE = 0x40000;

   /**
    * Factor to convert from ACC_SYNTHETIC_ATTRIBUTE to Opcode.ACC_SYNTHETIC.
    */
   static final int TO_ACC_SYNTHETIC = ACC_SYNTHETIC_ATTRIBUTE / ACC_SYNTHETIC;

   /**
    * The instruction types of all JVM opcodes.
    */
   static final byte[] TYPE;

   /**
    * The type of CONSTANT_Class constant pool items.
    */
   static final int CLASS = 7;

   /**
    * The type of CONSTANT_Fieldref constant pool items.
    */
   static final int FIELD = 9;

   /**
    * The type of CONSTANT_Methodref constant pool items.
    */
   static final int METH = 10;

   /**
    * The type of CONSTANT_InterfaceMethodref constant pool items.
    */
   static final int IMETH = 11;

   /**
    * The type of CONSTANT_String constant pool items.
    */
   static final int STR = 8;

   /**
    * The type of CONSTANT_Integer constant pool items.
    */
   static final int INT = 3;

   /**
    * The type of CONSTANT_Float constant pool items.
    */
   static final int FLOAT = 4;

   /**
    * The type of CONSTANT_Long constant pool items.
    */
   static final int LONG = 5;

   /**
    * The type of CONSTANT_Double constant pool items.
    */
   static final int DOUBLE = 6;

   /**
    * The type of CONSTANT_NameAndType constant pool items.
    */
   static final int NAME_TYPE = 12;

   /**
    * The type of CONSTANT_Utf8 constant pool items.
    */
   static final int UTF8 = 1;

   /**
    * The type of CONSTANT_MethodType constant pool items.
    */
   static final int MTYPE = 16;

   /**
    * The type of CONSTANT_MethodHandle constant pool items.
    */
   static final int HANDLE = 15;

   /**
    * The type of CONSTANT_InvokeDynamic constant pool items.
    */
   static final int INDY = 18;

   /**
    * The base value for all CONSTANT_MethodHandle constant pool items.
    * Internally, ASM store the 9 variations of CONSTANT_MethodHandle into 9 different items.
    */
   static final int HANDLE_BASE = 20;

   /**
    * Normal type Item stored in the ClassWriter {@link ClassWriter#typeTable}, instead of the constant pool, in order
    * to avoid clashes with normal constant pool items in the ClassWriter constant pool's hash table.
    */
   static final int TYPE_NORMAL = 30;

   /**
    * Uninitialized type Item stored in the ClassWriter {@link ClassWriter#typeTable}, instead of the constant pool, in
    * order to avoid clashes with normal constant pool items in the ClassWriter constant pool's hash table.
    */
   static final int TYPE_UNINIT = 31;

   /**
    * Merged type Item stored in the ClassWriter {@link ClassWriter#typeTable}, instead of the constant pool, in order
    * to avoid clashes with normal constant pool items in the ClassWriter constant pool's hash table.
    */
   static final int TYPE_MERGED = 32;

   /**
    * The type of BootstrapMethods items. These items are stored in a special class attribute named BootstrapMethods
    * and not in the constant pool.
    */
   static final int BSM = 33;

   /**
    * The class reader from which this class writer was constructed.
    */
   final ClassReader cr;

   /**
    * Minor and major version numbers of the class to be generated.
    */
   int version;

   /**
    * Index of the next item to be added in the constant pool.
    */
   int index;

   /**
    * The constant pool of this class.
    */
   final ByteVector pool;

   /**
    * The constant pool's hash table data.
    */
   Item[] items;

   /**
    * The threshold of the constant pool's hash table.
    */
   int threshold;

   /**
    * A reusable key used to look for items in the {@link #items} hash table.
    */
   final Item key;

   /**
    * A reusable key used to look for items in the {@link #items} hash table.
    */
   final Item key2;

   /**
    * A reusable key used to look for items in the {@link #items} hash table.
    */
   final Item key3;

   /**
    * A reusable key used to look for items in the {@link #items} hash table.
    */
   final Item key4;

   /**
    * A type table used to temporarily store internal names that will not necessarily be stored in the constant pool.
    * This type table is used by the control flow and data flow analysis algorithm used to compute stack map frames
    * from scratch. This array associates to each index <tt>i</tt> the Item whose index is <tt>i</tt>. All Item objects
    * stored in this array are also stored in the {@link #items} hash table. These two arrays allow to retrieve an Item
    * from its index or, conversely, to get the index of an Item from its value. Each Item stores an internal name in
    * its {@link Item#strVal1} field.
    */
   Item[] typeTable;

   /**
    * Number of elements in the {@link #typeTable} array.
    */
   private short typeCount;

   /**
    * The access flags of this class.
    */
   private int access;

   /**
    * The constant pool item that contains the internal name of this class.
    */
   private int name;

   /**
    * The internal name of this class.
    */
   String thisName;

   /**
    * The constant pool item that contains the signature of this class.
    */
   private int signature;

   /**
    * The constant pool item that contains the internal name of the super class of this class.
    */
   private int superName;

   /**
    * Number of interfaces implemented or extended by this class or interface.
    */
   private int interfaceCount;

   /**
    * The interfaces implemented or extended by this class or interface. More precisely, this array contains the
    * indexes of the constant pool items that contain the internal names of these interfaces.
    */
   private int[] interfaces;

   /**
    * The index of the constant pool item that contains the name of the source file from which this class was compiled.
    */
   private int sourceFile;

   /**
    * The SourceDebug attribute of this class.
    */
   private ByteVector sourceDebug;

   /**
    * The constant pool item that contains the name of the enclosing class of this class.
    */
   private int enclosingMethodOwner;

   /**
    * The constant pool item that contains the name and descriptor of the enclosing method of this class.
    */
   private int enclosingMethod;

   /**
    * The runtime visible annotations of this class.
    */
   private AnnotationWriter anns;

   /**
    * The number of entries in the InnerClasses attribute.
    */
   private int innerClassesCount;

   /**
    * The InnerClasses attribute.
    */
   private ByteVector innerClasses;

   /**
    * The number of entries in the BootstrapMethods attribute.
    */
   int bootstrapMethodsCount;

   /**
    * The BootstrapMethods attribute.
    */
   ByteVector bootstrapMethods;

   /**
    * The fields of this class.
    */
   private final List<FieldWriter> fields;

   /**
    * The methods of this class.
    */
   private final List<MethodWriter> methods;

   /**
    * <tt>true</tt> if the stack map frames must be recomputed from scratch.
    * <p/>
    * If this flag is set, then the calls to the {@link MethodVisitor#visitFrame} method are ignored, and the stack map
    * frames are recomputed from the methods bytecode. The arguments of the {@link MethodVisitor#visitMaxStack} method
    * are also ignored and recomputed from the bytecode. In other words, computeFrames implies computeMaxs.
    */
   private final boolean computeFrames;

   /*
    * Computes the instruction types of JVM opcodes.
    */
   static {
      byte[] b = new byte[220];
      String s =
         "AAAAAAAAAAAAAAAABCLMMDDDDDEEEEEEEEEEEEEEEEEEEEAAAAAAAADD" +
         "DDDEEEEEEEEEEEEEEEEEEEEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
         "AAAAAAAAAAAAAAAAANAAAAAAAAAAAAAAAAAAAAJJJJJJJJJJJJJJJJDOPAA" +
         "AAAAGGGGGGGHIFBFAAFFAARQJJKKJJJJJJJJJJJJJJJJJJ";

      for (int i = 0; i < b.length; ++i) {
         b[i] = (byte) (s.charAt(i) - 'A');
      }

      TYPE = b;
   }

   /**
    * Constructs a new {@link ClassWriter} object and enables optimizations for "mostly add" bytecode transformations.
    * These optimizations are the following:
    * <ul>
    * <li>The constant pool from the original class is copied as is in the new class, which saves time.
    * New constant pool entries will be added at the end if necessary, but unused constant pool entries <i>won't be
    * removed</i>.</li>
    * <li>Methods that are not transformed are copied as is in the new class, directly from the original class bytecode
    * (i.e. without emitting visit events for all the method instructions), which saves a <i>lot</i> of time.
    * Untransformed methods are detected by the fact that the {@link ClassReader} receives {@link MethodVisitor}
    * objects that come from a {@link ClassWriter} (and not from any other {@link ClassVisitor} instance).</li>
    * </ul>
    *
    * @param classReader the {@link ClassReader} used to read the original class. It will be used to copy the entire
    *                    constant pool from the original class and also to copy other fragments of original bytecode
    *                    where applicable.
    */
   public ClassWriter(ClassReader classReader) {
      index = 1;
      pool = new ByteVector();
      items = new Item[256];
      threshold = (int) (0.75d * items.length);
      key = new Item();
      key2 = new Item();
      key3 = new Item();
      key4 = new Item();

      int version = classReader.getVersion();
      computeFrames = version >= V1_7;

      classReader.copyPool(this);
      cr = classReader;

      fields = new ArrayList<FieldWriter>();
      methods = new ArrayList<MethodWriter>();
   }

   // ------------------------------------------------------------------------
   // Implementation of the ClassVisitor base class
   // ------------------------------------------------------------------------

   @Override
   public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      this.version = version;
      this.access = access;
      this.name = newClass(name);
      thisName = name;

      if (signature != null) {
         this.signature = newUTF8(signature);
      }

      this.superName = superName == null ? 0 : newClass(superName);

      if (interfaces != null && interfaces.length > 0) {
         interfaceCount = interfaces.length;
         this.interfaces = new int[interfaceCount];

         for (int i = 0; i < interfaceCount; ++i) {
            this.interfaces[i] = newClass(interfaces[i]);
         }
      }

      if (superName != null) {
         ClassLoad.addSuperClass(name, superName);
      }
   }

   @Override
   public void visitSource(String file, String debug) {
      if (file != null) {
         sourceFile = newUTF8(file);
      }

      if (debug != null) {
         sourceDebug = new ByteVector().encodeUTF8(debug, 0, Integer.MAX_VALUE);
      }
   }

   @Override
   public void visitOuterClass(String owner, String name, String desc) {
      enclosingMethodOwner = newClass(owner);

      if (name != null && desc != null) {
         enclosingMethod = newNameType(name, desc);
      }
   }

   @Override
   public AnnotationVisitor visitAnnotation(String desc) {
      ByteVector bv = new ByteVector();

      // Write type, and reserve space for values count.
      bv.putShort(newUTF8(desc)).putShort(0);

      AnnotationWriter aw = new AnnotationWriter(this, true, bv, bv, 2);
      aw.next = anns;
      anns = aw;
      return aw;
   }

   @Override
   public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (innerClasses == null) {
         innerClasses = new ByteVector();
      }

      // Sec. 4.7.6 of the JVMS states "Every CONSTANT_Class_info entry in the constant_pool table which represents a
      // class or interface C that is not a package member must have exactly one corresponding entry in the classes
      // array". To avoid duplicates we keep track in the intVal field of the Item of each CONSTANT_Class_info entry C
      // whether an inner class entry has already been added for C (this field is unused for class entries, and
      // changing its value does not change the hashcode and equality tests). If so we store the index of this inner
      // class entry (plus one) in intVal. This hack allows duplicate detection in O(1) time.
      Item nameItem = newClassItem(name);

      if (nameItem.intVal == 0) {
         ++innerClassesCount;
         innerClasses.putShort(nameItem.index);
         innerClasses.putShort(outerName == null ? 0 : newClass(outerName));
         innerClasses.putShort(innerName == null ? 0 : newUTF8(innerName));
         innerClasses.putShort(access);
         nameItem.intVal = innerClassesCount;
      }
      else {
         // Compare the inner classes entry nameItem.intVal - 1 with the arguments of this method and throw an
         // exception if there is a difference?
      }
   }

   @Override
   public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      FieldWriter field = new FieldWriter(this, access, name, desc, signature, value);
      fields.add(field);
      return field;
   }

   @Override
   public MethodWriter visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      MethodWriter method = new MethodWriter(this, access, name, desc, signature, exceptions, computeFrames);
      methods.add(method);
      return method;
   }

   // ------------------------------------------------------------------------
   // Other public methods
   // ------------------------------------------------------------------------

   /**
    * Returns the bytecode of the class that was build with this class writer.
    */
   @Override
   public byte[] toByteArray() {
      if (index > 0xFFFF) {
         throw new RuntimeException("Class file too large!");
      }

      // Computes the real size of the bytecode of this class.
      int size = 24 + 2 * interfaceCount;

      for (FieldWriter fb : fields) {
         size += fb.getSize();
      }

      for (MethodWriter mb : methods) {
         size += mb.getSize();
      }

      int attributeCount = 0;

      if (bootstrapMethods != null) {
         // We put it as first attribute in order to improve a bit ClassReader.copyBootstrapMethods.
         ++attributeCount;
         size += 8 + bootstrapMethods.length;
         newUTF8("BootstrapMethods");
      }

      if (signature != 0) {
         ++attributeCount;
         size += 8;
         newUTF8("Signature");
      }

      if (sourceFile != 0) {
         ++attributeCount;
         size += 8;
         newUTF8("SourceFile");
      }

      if (sourceDebug != null) {
         ++attributeCount;
         size += sourceDebug.length + 6;
         newUTF8("SourceDebugExtension");
      }

      if (enclosingMethodOwner != 0) {
         ++attributeCount;
         size += 10;
         newUTF8("EnclosingMethod");
      }

      if ((access & ACC_DEPRECATED) != 0) {
         ++attributeCount;
         size += 6;
         newUTF8("Deprecated");
      }

      if ((access & ACC_SYNTHETIC) != 0) {
         if (isPreJava5() || (access & ACC_SYNTHETIC_ATTRIBUTE) != 0) {
            ++attributeCount;
            size += 6;
            newUTF8("Synthetic");
         }
      }

      if (innerClasses != null) {
         ++attributeCount;
         size += 8 + innerClasses.length;
         newUTF8("InnerClasses");
      }

      if (anns != null) {
         ++attributeCount;
         size += 8 + anns.getSize();
         newUTF8("RuntimeVisibleAnnotations");
      }

      size += pool.length;

      // Allocates a byte vector of this size, in order to avoid unnecessary arraycopy operations in the
      // ByteVector.enlarge() method.
      ByteVector out = new ByteVector(size);
      out.putInt(0xCAFEBABE).putInt(version);
      out.putShort(index).putByteVector(pool);

      int mask = ACC_DEPRECATED | ACC_SYNTHETIC_ATTRIBUTE | ((access & ACC_SYNTHETIC_ATTRIBUTE) / TO_ACC_SYNTHETIC);
      out.putShort(access & ~mask).putShort(name).putShort(superName);

      out.putShort(interfaceCount);

      for (int i = 0; i < interfaceCount; ++i) {
         out.putShort(interfaces[i]);
      }

      out.putShort(fields.size());

      for (FieldWriter fb : fields) {
         fb.put(out);
      }

      out.putShort(methods.size());

      for (MethodWriter mb : methods) {
         mb.put(out);
      }

      out.putShort(attributeCount);

      if (bootstrapMethods != null) {
         out.putShort(newUTF8("BootstrapMethods"));
         out.putInt(bootstrapMethods.length + 2).putShort(bootstrapMethodsCount);
         out.putByteVector(bootstrapMethods);
      }

      if (signature != 0) {
         out.putShort(newUTF8("Signature")).putInt(2).putShort(signature);
      }

      if (sourceFile != 0) {
         out.putShort(newUTF8("SourceFile")).putInt(2).putShort(sourceFile);
      }

      if (sourceDebug != null) {
         out.putShort(newUTF8("SourceDebugExtension")).putInt(sourceDebug.length);
         out.putByteVector(sourceDebug);
      }

      if (enclosingMethodOwner != 0) {
         out.putShort(newUTF8("EnclosingMethod")).putInt(4);
         out.putShort(enclosingMethodOwner).putShort(enclosingMethod);
      }

      if ((access & ACC_DEPRECATED) != 0) {
         out.putShort(newUTF8("Deprecated")).putInt(0);
      }

      if ((access & ACC_SYNTHETIC) != 0) {
         if (isPreJava5() || (access & ACC_SYNTHETIC_ATTRIBUTE) != 0) {
            out.putShort(newUTF8("Synthetic")).putInt(0);
         }
      }

      if (innerClasses != null) {
         out.putShort(newUTF8("InnerClasses"));
         out.putInt(innerClasses.length + 2).putShort(innerClassesCount);
         out.putByteVector(innerClasses);
      }

      if (anns != null) {
         out.putShort(newUTF8("RuntimeVisibleAnnotations"));
         anns.put(out);
      }

      return out.data;
   }

   // ------------------------------------------------------------------------
   // Utility methods: version, constant pool management
   // ------------------------------------------------------------------------

   int getClassVersion() { return version & 0xFFFF; }
   boolean isPreJava5() { return getClassVersion() < V1_5; }

   /**
    * Adds a number or string constant to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param cst the value of the constant to be added to the constant pool. This parameter must be an {@link Integer},
    *            a {@link Float}, a {@link Long}, a {@link Double}, a {@link String} or a {@link Type}.
    * @return a new or already existing constant item with the given value.
    */
   Item newConstItem(Object cst) {
      if (cst instanceof String) {
         return newString((String) cst);
      }

      if (cst instanceof Integer) {
         return newInteger((Integer) cst);
      }

      if (cst instanceof Byte) {
         int val = ((Byte) cst).intValue();
         return newInteger(val);
      }

      if (cst instanceof Character) {
         return newInteger((int) (Character) cst);
      }

      if (cst instanceof Short) {
         int val = ((Short) cst).intValue();
         return newInteger(val);
      }

      if (cst instanceof Boolean) {
         int val = (Boolean) cst ? 1 : 0;
         return newInteger(val);
      }

      if (cst instanceof Float) {
         return newFloat((Float) cst);
      }

      if (cst instanceof Long) {
         return newLong((Long) cst);
      }

      if (cst instanceof Double) {
         return newDouble((Double) cst);
      }

      if (cst instanceof Type) {
         Type t = (Type) cst;
         int s = t.getSort();

         if (s == Type.OBJECT) {
            return newClassItem(t.getInternalName());
         }
         else if (s == Type.METHOD) {
            return newMethodTypeItem(t.getDescriptor());
         }
         else { // s == primitive type or array
            return newClassItem(t.getDescriptor());
         }
      }

      if (cst instanceof Handle) {
         return newHandleItem((Handle) cst);
      }

      throw new IllegalArgumentException("value " + cst);
   }

   /**
    * Adds an UTF8 string to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the String value.
    * @return the index of a new or already existing UTF8 item.
    */
   int newUTF8(String value) {
      key.set(UTF8, value, null, null);
      Item result = get(key);

      if (result == null) {
         pool.putByte(UTF8).putUTF8(value);
         result = new Item(index++, key);
         put(result);
      }

      return result.index;
   }

   /**
    * Adds a class reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the internal name of the class.
    * @return a new or already existing class reference item.
    */
   Item newClassItem(String value) {
      key2.set(CLASS, value, null, null);
      Item result = get(key2);

      if (result == null) {
         pool.put12(CLASS, newUTF8(value));
         result = new Item(index++, key2);
         put(result);
      }

      return result;
   }

   /**
    * Adds a class reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the internal name of the class.
    * @return the index of a new or already existing class reference item.
    */
   int newClass(String value) {
      return newClassItem(value).index;
   }

   /**
    * Adds a method type reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param methodDesc method descriptor of the method type.
    * @return a new or already existing method type reference item.
    */
   private Item newMethodTypeItem(String methodDesc) {
      key2.set(MTYPE, methodDesc, null, null);
      Item result = get(key2);

      if (result == null) {
         int itemIndex = newUTF8(methodDesc);
         pool.put12(MTYPE, itemIndex);
         result = new Item(index++, key2);
         put(result);
      }

      return result;
   }

   /**
    * Adds a handle to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @return a new or an already existing method type reference item.
    */
   private Item newHandleItem(Handle handle) {
      int tag = handle.tag;
      key4.set(HANDLE_BASE + tag, handle.owner, handle.name, handle.desc);
      Item result = get(key4);

      if (result == null) {
         Item item = tag <= H_PUTSTATIC ?
            newFieldItem(handle.owner, handle.name, handle.desc) :
            newMethodItem(handle.owner, handle.name, handle.desc, tag == H_INVOKEINTERFACE);
         put112(HANDLE, tag, item.index);

         result = new Item(index++, key4);
         put(result);
      }

      return result;
   }

   /**
    * Adds an invokedynamic reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param name    name of the invoked method.
    * @param desc    descriptor of the invoke method.
    * @param bsm     the bootstrap method.
    * @param bsmArgs the bootstrap method constant arguments.
    * @return a new or an already existing invokedynamic type reference item.
    */
   Item newInvokeDynamicItem(String name, String desc, Handle bsm, Object... bsmArgs) {
      // Cache for performance.
      ByteVector bootstrapMethods = this.bootstrapMethods;

      if (bootstrapMethods == null) {
         bootstrapMethods = this.bootstrapMethods = new ByteVector();
      }

      int position = bootstrapMethods.length; // record current position

      int hashCode = bsm.hashCode();
      Item handleItem = newHandleItem(bsm);
      bootstrapMethods.putShort(handleItem.index);

      int argsLength = bsmArgs.length;
      bootstrapMethods.putShort(argsLength);

      for (int i = 0; i < argsLength; i++) {
         Object bsmArg = bsmArgs[i];
         hashCode ^= bsmArg.hashCode();
         Item constItem = newConstItem(bsmArg);
         bootstrapMethods.putShort(constItem.index);
      }

      byte[] data = bootstrapMethods.data;
      int length = (1 + 1 + argsLength) << 1; // (bsm + argCount + arguments)
      hashCode &= 0x7FFFFFFF;
      Item result = items[hashCode % items.length];

   loop:
      while (result != null) {
         if (result.type != BSM || result.hashCode != hashCode) {
            result = result.next;
            continue;
         }

         // Because the data encode the size of the argument we don't need to test if these size are equals.
         int resultPosition = result.intVal;

         for (int p = 0; p < length; p++) {
            if (data[position + p] != data[resultPosition + p]) {
               result = result.next;
               continue loop;
            }
         }

         break;
      }

      int bootstrapMethodIndex;

      if (result != null) {
         bootstrapMethodIndex = result.index;
         bootstrapMethods.length = position; // revert to old position
      }
      else {
         bootstrapMethodIndex = bootstrapMethodsCount++;
         result = new Item(bootstrapMethodIndex);
         result.set(position, hashCode);
         put(result);
      }

      // Now, create the InvokeDynamic constant.
      key3.set(name, desc, bootstrapMethodIndex);
      result = get(key3);

      if (result == null) {
         put122(INDY, bootstrapMethodIndex, newNameType(name, desc));
         result = new Item(index++, key3);
         put(result);
      }

      return result;
   }

   /**
    * Adds a field reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param owner the internal name of the field's owner class.
    * @param name  the field's name.
    * @param desc  the field's descriptor.
    * @return a new or already existing field reference item.
    */
   Item newFieldItem(String owner, String name, String desc) {
      key3.set(FIELD, owner, name, desc);
      Item result = get(key3);

      if (result == null) {
         put122(FIELD, newClass(owner), newNameType(name, desc));
         result = new Item(index++, key3);
         put(result);
      }

      return result;
   }

   /**
    * Adds a method reference to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param owner the internal name of the method's owner class.
    * @param name  the method's name.
    * @param desc  the method's descriptor.
    * @param itf   <tt>true</tt> if <tt>owner</tt> is an interface.
    * @return a new or already existing method reference item.
    */
   Item newMethodItem(String owner, String name, String desc, boolean itf) {
      int type = itf ? IMETH : METH;
      key3.set(type, owner, name, desc);
      Item result = get(key3);

      if (result == null) {
         put122(type, newClass(owner), newNameType(name, desc));
         result = new Item(index++, key3);
         put(result);
      }

      return result;
   }

   /**
    * Adds an integer to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the int value.
    * @return a new or already existing int item.
    */
   Item newInteger(int value) {
      key.set(value);
      Item result = get(key);

      if (result == null) {
         pool.putByte(INT).putInt(value);
         result = new Item(index++, key);
         put(result);
      }

      return result;
   }

   /**
    * Adds a float to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the float value.
    * @return a new or already existing float item.
    */
   Item newFloat(float value) {
      key.set(value);
      Item result = get(key);

      if (result == null) {
         pool.putByte(FLOAT).putInt(key.intVal);
         result = new Item(index++, key);
         put(result);
      }

      return result;
   }

   /**
    * Adds a long to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the long value.
    * @return a new or already existing long item.
    */
   Item newLong(long value) {
      key.set(value);
      Item result = get(key);

      if (result == null) {
         pool.putByte(LONG).putLong(value);
         result = new Item(index, key);
         index += 2;
         put(result);
      }

      return result;
   }

   /**
    * Adds a double to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the double value.
    * @return a new or already existing double item.
    */
   Item newDouble(double value) {
      key.set(value);
      Item result = get(key);

      if (result == null) {
         pool.putByte(DOUBLE).putLong(key.longVal);
         result = new Item(index, key);
         index += 2;
         put(result);
      }

      return result;
   }

   /**
    * Adds a string to the constant pool of the class being built.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param value the String value.
    * @return a new or already existing string item.
    */
   private Item newString(String value) {
      key2.set(STR, value, null, null);
      Item result = get(key2);

      if (result == null) {
         pool.put12(STR, newUTF8(value));
         result = new Item(index++, key2);
         put(result);
      }

      return result;
   }

   /**
    * Adds a name and type to the constant pool of the class being build.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param name a name.
    * @param desc a type descriptor.
    * @return the index of a new or already existing name and type item.
    */
   private int newNameType(String name, String desc) {
      return newNameTypeItem(name, desc).index;
   }

   /**
    * Adds a name and type to the constant pool of the class being build.
    * Does nothing if the constant pool already contains a similar item.
    *
    * @param name a name.
    * @param desc a type descriptor.
    * @return a new or already existing name and type item.
    */
   private Item newNameTypeItem(String name, String desc) {
      key2.set(NAME_TYPE, name, desc, null);
      Item result = get(key2);

      if (result == null) {
         put122(NAME_TYPE, newUTF8(name), newUTF8(desc));
         result = new Item(index++, key2);
         put(result);
      }

      return result;
   }

   /**
    * Adds the given internal name to {@link #typeTable} and returns its index.
    * Does nothing if the type table already contains this internal name.
    *
    * @param type the internal name to be added to the type table.
    * @return the index of this internal name in the type table.
    */
   int addType(String type) {
      key.set(TYPE_NORMAL, type, null, null);
      Item result = get(key);

      if (result == null) {
         result = addType();
      }

      return result.index;
   }

   /**
    * Adds the given "uninitialized" type to {@link #typeTable} and returns its index.
    * This method is used for UNINITIALIZED types, made of an internal name and a bytecode offset.
    *
    * @param type   the internal name to be added to the type table.
    * @param offset the bytecode offset of the NEW instruction that created this UNINITIALIZED type value.
    * @return the index of this internal name in the type table.
    */
   int addUninitializedType(String type, int offset) {
      key.type = TYPE_UNINIT;
      key.intVal = offset;
      key.strVal1 = type;
      key.hashCode = 0x7FFFFFFF & (TYPE_UNINIT + type.hashCode() + offset);
      Item result = get(key);

      if (result == null) {
         result = addType();
      }

      return result.index;
   }

   /**
    * Adds the given Item to {@link #typeTable}.
    *
    * @return the added Item, which a new Item instance with the same value as the given Item.
    */
   private Item addType() {
      ++typeCount;
      Item result = new Item(typeCount, key);
      put(result);

      if (typeTable == null) {
         typeTable = new Item[16];
      }

      if (typeCount == typeTable.length) {
         Item[] newTable = new Item[2 * typeTable.length];
         System.arraycopy(typeTable, 0, newTable, 0, typeTable.length);
         typeTable = newTable;
      }

      typeTable[typeCount] = result;
      return result;
   }

   /**
    * Returns the index of the common super type of the two given types. This method calls {@link #getCommonSuperClass}
    * and caches the result in the {@link #items} hash table to speedup future calls with the same parameters.
    *
    * @param type1 index of an internal name in {@link #typeTable}.
    * @param type2 index of an internal name in {@link #typeTable}.
    * @return the index of the common super type of the two given types.
    */
   int getMergedType(int type1, int type2) {
      key2.type = TYPE_MERGED;
      key2.longVal = type1 | ((long) type2 << 32);
      key2.hashCode = 0x7FFFFFFF & (TYPE_MERGED + type1 + type2);
      Item result = get(key2);

      if (result == null) {
         String t = typeTable[type1].strVal1;
         String u = typeTable[type2].strVal1;
         key2.intVal = addType(getCommonSuperClass(t, u));
         result = new Item((short) 0, key2);
         put(result);
      }

      return result.intVal;
   }

   /**
    * Returns the common super type of the two given types. The default implementation of this method <i>loads</i> the
    * two given classes and uses the java.lang.Class methods to find the common super class. It can be overridden to
    * compute this common super type in other ways, in particular without actually loading any class, or to take into
    * account the class that is currently being generated by this ClassWriter, which can of course not be loaded since
    * it is under construction.
    *
    * @param type1 the internal name of a class.
    * @param type2 the internal name of another class.
    * @return the internal name of the common super class of the two given classes.
    */
   private String getCommonSuperClass(String type1, String type2) {
      // Reimplemented to avoid "duplicate class definition" errors.
      String class1 = type1;
      String class2 = type2;

      while (true) {
         if (OBJECT.equals(class1) || OBJECT.equals(class2)) {
            return OBJECT;
         }

         String superClass = ClassLoad.whichIsSuperClass(class1, class2);

         if (superClass != null) {
            return superClass;
         }

         class1 = ClassLoad.getSuperClass(class1);
         class2 = ClassLoad.getSuperClass(class2);

         if (class1.equals(class2)) {
            return class1;
         }
      }
   }

   /**
    * Returns the constant pool's hash table item which is equal to the given item.
    *
    * @param key a constant pool item.
    * @return the constant pool's hash table item which is equal to the given item, or <tt>null</tt> if there is no
    * such item.
    */
   private Item get(Item key) {
      Item item = items[key.hashCode % items.length];

      while (item != null && (item.type != key.type || !key.isEqualTo(item))) {
         item = item.next;
      }

      return item;
   }

   /**
    * Puts the given item in the constant pool's hash table. The hash table <i>must</i> not already contains this item.
    *
    * @param i the item to be added to the constant pool's hash table.
    */
   private void put(Item i) {
      if (index + typeCount > threshold) {
         int ll = items.length;
         int nl = ll * 2 + 1;
         Item[] newItems = new Item[nl];

         for (int l = ll - 1; l >= 0; --l) {
            Item j = items[l];

            while (j != null) {
               int index = j.hashCode % newItems.length;
               Item k = j.next;
               j.next = newItems[index];
               newItems[index] = j;
               j = k;
            }
         }

         items = newItems;
         threshold = (int) (nl * 0.75);
      }

      int index = i.hashCode % items.length;
      i.next = items[index];
      items[index] = i;
   }

   /**
    * Puts one byte and two shorts into the constant pool.
    *
    * @param b  a byte.
    * @param s1 a short.
    * @param s2 another short.
    */
   private void put122(int b, int s1, int s2) {
      pool.put12(b, s1).putShort(s2);
   }

   /**
    * Puts two bytes and one short into the constant pool.
    *
    * @param b1 a byte.
    * @param b2 another byte.
    * @param s  a short.
    */
   private void put112(int b1, int b2, int s) {
      pool.put11(b1, b2).putShort(s);
   }
}
