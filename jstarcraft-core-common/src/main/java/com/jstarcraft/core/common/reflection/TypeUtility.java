package com.jstarcraft.core.common.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.jstarcraft.core.utility.ClassUtility;

/**
 * 类型工具
 * 
 * @author Birdy
 *
 */
public class TypeUtility extends TypeUtils {

    private final static Type[] emptyTypes = new Type[0];

    public static Type refineType(Type fromType, Class<?> toType, Type... context) {
        if (fromType == null) {
            return null;
        }
        Type[] types = emptyTypes;
        if (fromType instanceof ParameterizedType && TypeUtility.isAssignable(fromType, toType)) {
            ParameterizedType parameterizedType = ParameterizedType.class.cast(fromType);
            types = parameterizedType.getActualTypeArguments();
            int cursor = 0;
            for (int index = 0, size = types.length; index < size; index++) {
                Type type = types[index];
                if (type instanceof TypeVariable || type instanceof WildcardType) {
                    if (context.length > cursor) {
                        types[index] = context[cursor++];
                    }
                }
            }
            if (types.length == toType.getTypeParameters().length) {
                return TypeUtility.parameterize(toType, types);
            }
        }
        Class clazz = TypeUtility.getRawType(fromType, null);
        Type[] interfaceTypes = clazz.getGenericInterfaces();
        for (Type interfaceType : interfaceTypes) {
            Type type = refineType(interfaceType, toType, types);
            if (type != null) {
                return type;
            }
        }
        return refineType(clazz.getGenericSuperclass(), toType, types);
    }

    private static final class TypeVariableImpl<D extends GenericDeclaration> implements TypeVariable<D> {

        private final D declaration;

        private final String name;

        private final Type[] bounds;

        private TypeVariableImpl(D declaration, String name, Type[] bounds) {
            this.declaration = declaration;
            this.name = name;
            this.bounds = bounds;
        }

        @Override
        public Type[] getBounds() {
            return bounds;
        }

        @Override
        public D getGenericDeclaration() {
            return declaration;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Annotation[] getAnnotations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof TypeVariable) {
                TypeVariable<?> that = (TypeVariable<?>) object;
                return this.name.equals(that.getName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }

    }

    public static <D extends GenericDeclaration> TypeVariable<D> typeVariable(D declaration, String name, Type... bounds) {
        return new TypeVariableImpl<>(declaration, name, bounds);
    }

    private static String class2String(Class<?> clazz) {
        if (clazz.isArray()) {
            return type2String(clazz.getComponentType()) + "[]";
        }

        StringBuilder buffer = new StringBuilder();
        if (clazz.getEnclosingClass() != null) {
            buffer.append(class2String(clazz.getEnclosingClass())).append('.').append(clazz.getSimpleName());
        } else {
            buffer.append(clazz.getName());
        }
        if (clazz.getTypeParameters().length > 0) {
            buffer.append('<');
            type2Buffer(buffer, ", ", clazz.getTypeParameters());
            buffer.append('>');
        }
        return buffer.toString();
    }

    private static String typeVariable2String(TypeVariable<?> typeVariable) {
        StringBuilder buffer = new StringBuilder(typeVariable.getName());
        Type[] bounds = typeVariable.getBounds();
        if (bounds.length > 0 && !(bounds.length == 1 && Object.class.equals(bounds[0]))) {
            buffer.append(" extends ");
            type2Buffer(buffer, " & ", typeVariable.getBounds());
        }
        return buffer.toString();
    }

    private static String parameterizedType2String(ParameterizedType parameterizedType) {
        StringBuilder buffer = new StringBuilder();
        Type type = parameterizedType.getOwnerType();
        Class<?> clazz = (Class<?>) parameterizedType.getRawType();
        if (type == null) {
            buffer.append(clazz.getName());
        } else {
            if (type instanceof Class<?>) {
                buffer.append(((Class<?>) type).getName());
            } else {
                buffer.append(type.toString());
            }
            buffer.append('.').append(clazz.getSimpleName());
        }

        int[] recursiveIndexes = recursiveIndexes(parameterizedType);
        if (recursiveIndexes.length > 0) {
            type2Buffer(buffer, recursiveIndexes, parameterizedType.getActualTypeArguments());
        } else {
            type2Buffer(buffer.append('<'), ", ", parameterizedType.getActualTypeArguments()).append('>');
        }
        return buffer.toString();
    }

    private static void type2Buffer(StringBuilder buffer, int[] recursiveIndexes, Type[] argumentTypes) {
        for (int index = 0; index < recursiveIndexes.length; index++) {
            type2Buffer(buffer.append('<'), ", ", argumentTypes[index].toString()).append('>');
        }
        argumentTypes = ArrayUtils.removeAll(argumentTypes, recursiveIndexes);
        if (argumentTypes.length > 0) {
            type2Buffer(buffer.append('<'), ", ", argumentTypes).append('>');
        }
    }

    private static int[] recursiveIndexes(ParameterizedType parameterizedType) {
        Type[] argumentTypes = Arrays.copyOf(parameterizedType.getActualTypeArguments(), parameterizedType.getActualTypeArguments().length);
        int[] recursiveIndexes = {};
        for (int index = 0; index < argumentTypes.length; index++) {
            if (argumentTypes[index] instanceof TypeVariable<?>) {
                if (isRecursive(((TypeVariable<?>) argumentTypes[index]), parameterizedType)) {
                    recursiveIndexes = ArrayUtils.add(recursiveIndexes, index);
                }
            }
        }
        return recursiveIndexes;
    }

    private static boolean isRecursive(TypeVariable<?> typeVariable, ParameterizedType parameterizedType) {
        return ArrayUtils.contains(typeVariable.getBounds(), parameterizedType);
    }

    private static String wildcardType2String(WildcardType wildcardType) {
        StringBuilder buffer = new StringBuilder().append('?');
        Type[] lowerBounds = wildcardType.getLowerBounds();
        Type[] upperBounds = wildcardType.getUpperBounds();
        if (lowerBounds.length > 1 || lowerBounds.length == 1 && lowerBounds[0] != null) {
            type2Buffer(buffer.append(" super "), " & ", lowerBounds);
        } else if (upperBounds.length > 1 || upperBounds.length == 1 && !Object.class.equals(upperBounds[0])) {
            type2Buffer(buffer.append(" extends "), " & ", upperBounds);
        }
        return buffer.toString();
    }

    private static String genericArrayType2String(GenericArrayType genericArrayType) {
        return String.format("%s<>", type2String(genericArrayType.getGenericComponentType()));
    }

    private static <T> StringBuilder type2Buffer(StringBuilder buffer, String separator, T... types) {
        Validate.notEmpty(Validate.noNullElements(types));
        if (types.length > 0) {
            buffer.append(object2String(types[0]));
            for (int index = 1; index < types.length; index++) {
                buffer.append(separator).append(object2String(types[index]));
            }
        }
        return buffer;
    }

    private static <T> String object2String(T object) {
        return object instanceof Type ? type2String((Type) object) : object.toString();
    }

    public static String type2String(Type type) {
        Validate.notNull(type);
        if (type instanceof Class<?>) {
            return class2String((Class<?>) type);
        }
        if (type instanceof ParameterizedType) {
            return parameterizedType2String((ParameterizedType) type);
        }
        if (type instanceof WildcardType) {
            return wildcardType2String((WildcardType) type);
        }
        if (type instanceof TypeVariable<?>) {
            return typeVariable2String((TypeVariable<?>) type);
        }
        if (type instanceof GenericArrayType) {
            return genericArrayType2String((GenericArrayType) type);
        }
        throw new IllegalArgumentException(ObjectUtils.identityToString(type));
    }

    private static final Map<String, String> long2Short = new HashMap<>();

    static {
        long2Short.put("boolean", "Z");
        long2Short.put("byte", "B");
        long2Short.put("char", "C");
        long2Short.put("short", "S");
        long2Short.put("int", "I");
        long2Short.put("long", "L");
        long2Short.put("double", "D");
        long2Short.put("float", "F");
    }

    public static Type string2Type(String string) {
        CharStream characters = CharStreams.fromString(string);
        TypeLexer lexer = new TypeLexer(characters); // 词法分析
        TokenStream tokens = new CommonTokenStream(lexer); // 转成token流
        TypeParser parser = new TypeParser(tokens); // 语法分析
        ParseTree tree = parser.type();

        ParseTreeWalker walker = new ParseTreeWalker();
        LinkedList<Type> stack = new LinkedList<>();
        TypeListener listener = new TypeBaseListener() {

            @Override
            public void exitArray(TypeParser.ArrayContext context) {
                int dimension = context.ARRAY().size();
                Type type = stack.pop();
                if (dimension > 0) {
                    StringBuilder buffer = new StringBuilder();
                    for (int index = 0; index < dimension; index++) {
                        buffer.append("[");
                    }
                    buffer.append(long2Short.getOrDefault(type.getTypeName(), "L" + type.getTypeName() + ";"));
                    try {
                        type = ClassUtility.getClass(buffer.toString());
                    } catch (ClassNotFoundException exception) {
                        throw new RuntimeException(exception);
                    }
                }
                dimension = context.GENERIC().size();
                for (int index = 0; index < dimension; index++) {
                    type = TypeUtility.genericArrayType(type);
                }
                stack.push(type);
            }

            @Override
            public void exitClazz(TypeParser.ClazzContext context) {
                try {
                    stack.push(ClassUtility.getClass(context.getText()));
                } catch (ClassNotFoundException e) {
                    stack.push(TypeUtility.typeVariable(null, context.getText()));
                }
            }

            @Override
            public void exitGeneric(TypeParser.GenericContext context) {
                int size = context.type().size();
                Type[] types = new Type[size];
                for (int index = size - 1; index >= 0; index--) {
                    types[index] = stack.pop();
                }
                Type type = stack.pop();
                type = TypeUtility.parameterize((Class) type, types);
                stack.push(type);
            }

            @Override
            public void exitVariable(TypeParser.VariableContext context) {
                int size = context.generic().size();
                Type[] types = new Type[size];
                for (int index = size - 1; index >= 0; index--) {
                    types[index] = stack.pop();
                }
                Type type = TypeUtility.typeVariable(null, context.ID().getText(), types);
                stack.push(type);
            }

            @Override
            public void exitWildcard(TypeParser.WildcardContext context) {
                Type type = stack.pop();
                WildcardTypeBuilder builder = TypeUtility.wildcardType();
                TerminalNode bound = context.BOUND();
                if (bound != null) {
                    if (bound.getText().equals("extends")) {
                        builder.withUpperBounds(type);
                    } else {
                        builder.withLowerBounds(type);
                    }
                }
                type = builder.build();
                stack.push(type);
            }

        };
        walker.walk(listener, tree);
        return stack.pop();
    }

}
