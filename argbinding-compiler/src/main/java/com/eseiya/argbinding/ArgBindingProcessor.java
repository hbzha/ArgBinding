/*
 * Copyright (C) 2019 AndyZheng.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eseiya.argbinding;

import com.eseiya.argbinding.annotation.BindArg;
import com.eseiya.argbinding.annotation.BindTarget;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * The processor for arg binding.
 *
 * @author AndyZheng
 * @since 2019/2/12
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ArgBindingProcessor extends AbstractProcessor {

    public static final String GENERATED_FILE_COMMENT = "THIS CODE IS GENERATED BY ArgBinding, DO NOT EDIT.";
    public static final String STRING = "java.lang.String";
    public static final String SERIALIZABLE = "java.io.Serializable";
    public static final String PARCELABLE = "android.os.Parcelable";
    public static final String ACTIVITY = "android.app.Activity";
    public static final String CONTEXT = "android.content.Context";
    public static final String FRAGMENT = "android.app.Fragment";
    public static final String V4_FRAGMENT = "android.support.v4.app.Fragment";
    public static final String SERVICE = "android.app.Service";

    private static final ClassName BUNDLE_CLASS = ClassName.bestGuess("android.os.Bundle");
    private static final ClassName PARCELABLE_CLASS = ClassName.bestGuess(PARCELABLE);
    private static final ClassName ARG_BUILDER_CLASS = ClassName.bestGuess("com.eseiya.argbinding.ArgBuilder");
    private static final ClassName ACTIVITY_ARG_BUILDER_CLASS = ClassName.bestGuess("com.eseiya.argbinding.ActivityArgBuilder");
    private static final ClassName SERVICE_ARG_BUILDER_CLASS = ClassName.bestGuess("com.eseiya.argbinding.ServiceArgBuilder");
    private static final ClassName ARG_BINDER_CLASS = ClassName.bestGuess("com.eseiya.argbinding.ArgBinder");

    private TypeMirror activityType;
    private TypeMirror fragmentType;
    private TypeMirror v4FragmentType;
    private TypeMirror serviceType;
    private TypeMirror parcelableType;
    private TypeMirror serializableType;

    private Filer filer;
    private Types typeUtil;
    private Elements elementsUtil;
    private Logger logger;

    // target and field need bind
    private Map<TypeElement, List<Element>> targetAndFields = new HashMap<>();
    // target's parent
    private Map<TypeElement, TypeElement> targetParents = new HashMap<>();
    // the method type in bundle
    private Map<String, String> bundleMethodTypes = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        logger = new Logger(processingEnv.getMessager());
        filer = processingEnv.getFiler();
        typeUtil = processingEnv.getTypeUtils();
        elementsUtil = processingEnv.getElementUtils();

        activityType = elementsUtil.getTypeElement(ACTIVITY).asType();
        fragmentType = elementsUtil.getTypeElement(FRAGMENT).asType();
        v4FragmentType = elementsUtil.getTypeElement(V4_FRAGMENT).asType();
        serviceType = elementsUtil.getTypeElement(SERVICE).asType();
        parcelableType = elementsUtil.getTypeElement(PARCELABLE).asType();
        serializableType = elementsUtil.getTypeElement(SERIALIZABLE).asType();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BindArg.class.getCanonicalName());
        types.add(BindTarget.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (ProcessorUtils.isEmpty(set)) {
            return false;
        }
        logger.info("BindArg processor begin.");

        try {
            findField(roundEnvironment.getElementsAnnotatedWith(BindArg.class));
            findTarget(roundEnvironment.getElementsAnnotatedWith(BindTarget.class));
            findTargetParent();
            generateBuilderAndBinder();
        } catch (Exception e) {
            logger.error(e);
        }
        logger.info("BindArg processor end.");
        return true;
    }

    /**
     * Find the field with annotation {@link BindArg}.
     */
    private void findField(Set<? extends Element> elements) {
        if (ProcessorUtils.isEmpty(elements)) {
            return;
        }
        for (Element element : elements) {
            TypeElement targetElement = (TypeElement) element.getEnclosingElement();
            checkField(targetElement, element);
            putFieldElement(targetElement, element);
        }
    }

    /**
     * Find the field with annotation {@link BindTarget}.
     */
    private void findTarget(Set<? extends Element> elements) {
        if (ProcessorUtils.isEmpty(elements)) {
            return;
        }
        for (Element enclosingElement : elements) {
            if (!targetAndFields.containsKey(enclosingElement)) {
                putFieldElement((TypeElement) enclosingElement, null);
            }
        }
    }

    private List<Element> putFieldElement(TypeElement targetElement, Element fieldElement) {
        checkTarget(targetElement);
        List<Element> fields = targetAndFields.get(targetElement);
        if (fields == null) {
            fields = new ArrayList<>();
            targetAndFields.put(targetElement, fields);
        }
        if (fieldElement != null) {
            fields.add(fieldElement);
        }
        return fields;
    }

    /**
     * Check whether the bind field is legal.
     */
    private void checkField(TypeElement targetElement, Element fieldElement) {
        Set<Modifier> modifiers = fieldElement.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC) ||
                modifiers.contains(Modifier.FINAL)) {
            ProcessorUtils.error("The bind field must not be private or static or final.[%s.%s]", targetElement.getQualifiedName(), fieldElement.getSimpleName());
        }
        String type = getBundleMethodType(fieldElement);
        if (type == null) {
            ProcessorUtils.error("The type[%s] is not support.[%s.%s]", fieldElement.asType(), targetElement.getQualifiedName(), fieldElement.getSimpleName());
        }
    }

    /**
     * Find target's parent.
     */
    private void findTargetParent() {
        for (TypeElement targetElement : targetAndFields.keySet()) {
            TypeElement superTypeElement = findTargetParent(targetElement);
            if (superTypeElement != null) {
                targetParents.put(targetElement, superTypeElement);
            }
        }
    }

    /**
     * Find target's parent.
     */
    private TypeElement findTargetParent(TypeElement targetElement) {
        TypeMirror superTypeMirror = targetElement.getSuperclass();
        if (superTypeMirror.getKind() == TypeKind.NONE || CommonUtils.isFrameworkPackage(superTypeMirror.toString())) {
            return null;
        }
        TypeElement superTypeElement = (TypeElement) ((DeclaredType) superTypeMirror).asElement();
        if (targetAndFields.containsKey(superTypeElement)) {
            return superTypeElement;
        } else {
            return findTargetParent(superTypeElement);
        }
    }

    private void generateBuilderAndBinder() throws IOException {
        if (targetAndFields.isEmpty()) {
            return;
        }
        for (Map.Entry<TypeElement, List<Element>> entry : targetAndFields.entrySet()) {
            TypeElement target = entry.getKey();
            List<Element> fields = entry.getValue();
            generateBuilder(target, getSuperFields(target, fields));
            generateBinder(target, fields);
        }
    }

    /**
     * Get all super fields.
     */
    private List<Element> getSuperFields(TypeElement targetElement, List<Element> fields) {
        TypeElement superElement = targetParents.get(targetElement);
        if (superElement == null) {
            return fields;
        } else {
            List<Element> allFields = new ArrayList<>(fields);
            do {
                // add the super fields to the head, priority processing super fields
                allFields.addAll(0, targetAndFields.get(superElement));
                superElement = targetParents.get(superElement);
            } while (superElement != null);
            return allFields;
        }
    }

    private void generateBuilder(TypeElement targetElement, List<Element> fields) throws IOException {
        ClassName targetTypeName = ClassName.get(targetElement);
        boolean isContext = isActivity(targetElement) || isService(targetElement);
        ClassName builderTypeName = ClassName.bestGuess(targetElement.getQualifiedName() + CommonConstants.BUILDER_NAME_SUFFIX);

        boolean isPublic = targetElement.getModifiers().contains(Modifier.PUBLIC);
        //abstract fragment
        boolean isAbstract = !isContext && targetElement.getModifiers().contains(Modifier.ABSTRACT);

        TypeName superTypeName = getSuperBuilderTypeName(targetElement, builderTypeName);
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(builderTypeName)
                .addJavadoc("The ArgBuilder for {@link $N}.\n", targetElement.getQualifiedName())
                .superclass(superTypeName);
        if (isPublic) {
            typeBuilder.addModifiers(PUBLIC);
        }
        if (isAbstract) {
            typeBuilder.addModifiers(Modifier.ABSTRACT);
        }

        if (!isAbstract) { //Activity and not abstract Fragment add newBuilder method
            MethodSpec.Builder newBuilderMethodBuilder = MethodSpec.methodBuilder("newBuilder")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(builderTypeName)
                    .addStatement("return new $T()", builderTypeName);
            typeBuilder.addMethod(newBuilderMethodBuilder.build());
        }

        // not abstract Fragment add build method
        if (!isContext && !isAbstract) {
            MethodSpec.Builder builderMethodBuilder = MethodSpec.methodBuilder("build")
                    .addJavadoc("Build the fragment.The fragment must have an empty constructor.\n")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(targetTypeName)
                    .addStatement("$T fragment = new $T()", targetTypeName, targetTypeName)
                    .addStatement("fragment.setArguments(args)")
                    .addStatement("return fragment");
            typeBuilder.addMethod(builderMethodBuilder.build());
        }

        // Activity add getTargetClass method
        if (isContext) {
            MethodSpec.Builder getTargetClassBuilder = MethodSpec.methodBuilder("getTargetClass")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(ClassName.bestGuess(CONTEXT))))
                    .addStatement("return $T.class", targetTypeName);
            typeBuilder.addMethod(getTargetClassBuilder.build());
        }

        // add set method
        Map<String, Element> allFiledNames = new HashMap<>(fields.size());
        for (Element fieldElement : fields) {
            BindArg fieldConfig = fieldElement.getAnnotation(BindArg.class);
            String fieldName = fieldElement.getSimpleName().toString();
            String enclosingElementName = ((TypeElement) fieldElement.getEnclosingElement()).getQualifiedName().toString();

            String fieldAlias;
            if (!ProcessorUtils.isEmpty(fieldConfig.value())) {
                fieldAlias = fieldConfig.value();
                if (!SourceVersion.isIdentifier(fieldAlias)) {
                    ProcessorUtils.error("[%s] is not a valid alias.[%s.%s]", fieldAlias, enclosingElementName, fieldName);
                }
            } else {
                fieldAlias = fieldName;
            }

            // check fields conflict
            if (!allFiledNames.containsKey(fieldAlias)) {
                allFiledNames.put(fieldAlias, fieldElement);
            } else {
                Element existElement = allFiledNames.get(fieldAlias);
                String existEnclosingElementName = ((TypeElement) existElement.getEnclosingElement()).getQualifiedName().toString();
                ProcessorUtils.error("The field or alias already exists,[%s.%s] and [%s.%s] conflicts.", enclosingElementName, fieldName,
                        existEnclosingElementName, existElement.getSimpleName());
            }

            MethodSpec.Builder setMethodBuilder = MethodSpec.methodBuilder("set" + ProcessorUtils.toFirstLetterUpperCase(fieldAlias))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderTypeName)
                    .addParameter(TypeName.get(fieldElement.asType()), fieldAlias)
                    .addStatement("args.put" + getBundleMethodType(fieldElement) + "($S,$N)", fieldAlias, fieldAlias)
                    .addStatement("return self()");
            String docString = elementsUtil.getDocComment(fieldElement);
            if (!ProcessorUtils.isEmpty(docString)) {
                setMethodBuilder.addJavadoc(docString);
            }
            setMethodBuilder.addJavadoc("@see $N#$N\n", enclosingElementName, fieldName);
            typeBuilder.addMethod(setMethodBuilder.build());
        }
        JavaFile.builder(builderTypeName.packageName(), typeBuilder.build())
                .addFileComment(GENERATED_FILE_COMMENT)
                .build()
                .writeTo(filer);
    }

    private void generateBinder(TypeElement targetElement, List<Element> fields) throws IOException {
        ClassName builderTypeName = ClassName.bestGuess(targetElement.getQualifiedName() + CommonConstants.BINDER_NAME_SUFFIX);
        TypeElement superTypeElement = targetParents.get(targetElement);
        ClassName targetTypeName = ClassName.get(targetElement);

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(builderTypeName)
                .addJavadoc("The ArgBinder for {@link $N}.\n", targetElement.getQualifiedName())
                .addTypeVariable(TypeVariableName.get("T", targetTypeName))
                .superclass(getSuperBinderTypeName(superTypeElement));
        boolean isPublic = targetElement.getModifiers().contains(Modifier.PUBLIC);
        boolean isAbstract = targetElement.getModifiers().contains(Modifier.ABSTRACT);
        if (isPublic) {
            typeBuilder.addModifiers(PUBLIC);
        }
        if (isAbstract) {
            typeBuilder.addModifiers(Modifier.ABSTRACT);
        }

        // add bindArgs method
        MethodSpec.Builder bindArgsMethodBuilder = MethodSpec.methodBuilder("bindArgs")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .addParameter(TypeVariableName.get("T"), "target")
                .addParameter(BUNDLE_CLASS, "args");
        if (superTypeElement != null) {
            bindArgsMethodBuilder.addStatement("super.bindArgs(target, args)");
        }

        // add checkRequiredArg method
        MethodSpec.Builder requiredMethodBuilder = MethodSpec.methodBuilder("checkRequiredArg")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .addParameter(BUNDLE_CLASS, "args");
        if (superTypeElement != null) {
            requiredMethodBuilder.addStatement("super.checkRequiredArg(args)");
        }

        boolean hasRequiredField = false;
        for (Element fieldElement : fields) {
            TypeName typeName = TypeName.get(fieldElement.asType());
            BindArg fieldConfig = fieldElement.getAnnotation(BindArg.class);
            String fieldName = fieldElement.getSimpleName().toString();
            String fieldAlias;
            if (!ProcessorUtils.isEmpty(fieldConfig.value())) {
                fieldAlias = fieldConfig.value();
            } else {
                fieldAlias = fieldName;
            }

            // add bindArgs method code
            if (typeName.isPrimitive() || typeName.toString().equals(STRING)) {
                bindArgsMethodBuilder.addStatement("target.$N = args.get" + getBundleMethodType(fieldElement) + "($S,target.$N)", fieldName, fieldAlias, fieldName);
            } else if (isParcelableArray(fieldElement.asType())) {//Parcelable[]
                TypeName parcelableArrayName = ArrayTypeName.of(PARCELABLE_CLASS);
                bindArgsMethodBuilder.beginControlFlow("if (args.containsKey($S))", fieldAlias)
                        .addStatement("$T $N = ($T) args.getSerializable($S)", parcelableArrayName, fieldName, parcelableArrayName, fieldAlias)
                        .beginControlFlow("if ($N == null)", fieldName)
                        .addStatement("target.$N = null", fieldName)
                        .endControlFlow()
                        .beginControlFlow("else")
                        .addStatement("target.$N = new $T[$N.length]", fieldName, ((ArrayType) fieldElement.asType()).getComponentType(), fieldName)
                        .addStatement("System.arraycopy($N, 0, target.$N, 0, $N.length)", fieldName, fieldName, fieldName)
                        .endControlFlow()
                        .endControlFlow();
            } else {
                bindArgsMethodBuilder.beginControlFlow("if (args.containsKey($S))", fieldAlias)
                        .addStatement("target.$N = ($T)args.get" + getBundleMethodType(fieldElement) + "($S)", fieldName, typeName, fieldAlias)
                        .endControlFlow();
            }

            // add checkRequiredArg method code
            if (fieldConfig.required()) {
                if (!hasRequiredField) {
                    hasRequiredField = true;
                    requiredMethodBuilder.beginControlFlow("if (args == null)")
                            .addStatement("throw new RuntimeException(\"args == null and has field is required in $T\")", targetTypeName)
                            .endControlFlow()
                            .addStatement("$T emptyFields = new $T<>()", ParameterizedTypeName.get(List.class, String.class), ArrayList.class);
                }

                String fieldWithAlias;
                if (fieldName.equals(fieldAlias)) {
                    fieldWithAlias = fieldName;
                } else {
                    fieldWithAlias = fieldName + "[" + fieldAlias + "]";
                }
                requiredMethodBuilder.beginControlFlow("if (args.get($S) == null)", fieldAlias)
                        .addStatement("emptyFields.add($S)", fieldWithAlias)
                        .endControlFlow();
            }
        }
        if (hasRequiredField) {
            requiredMethodBuilder.beginControlFlow("if (!emptyFields.isEmpty())")
                    .addStatement("throw new RuntimeException(\"The field \" + emptyFields + \" is required in $N\")", targetElement.getQualifiedName())
                    .endControlFlow();
        }

        typeBuilder.addMethod(bindArgsMethodBuilder.build());
        typeBuilder.addMethod(requiredMethodBuilder.build());
        JavaFile.builder(builderTypeName.packageName(), typeBuilder.build())
                .addFileComment(GENERATED_FILE_COMMENT)
                .build()
                .writeTo(filer);
    }

    private TypeName getSuperBuilderTypeName(TypeElement targetElement, ClassName builderTypeName) {
        TypeName superTypeName;
        if (isActivity(targetElement)) {
            superTypeName = ACTIVITY_ARG_BUILDER_CLASS;
        } else if (isService(targetElement)) {
            superTypeName = SERVICE_ARG_BUILDER_CLASS;
        } else {
            superTypeName = ARG_BUILDER_CLASS;
        }
        superTypeName = ParameterizedTypeName.get((ClassName) superTypeName, builderTypeName);
        return superTypeName;
    }

    private TypeName getSuperBinderTypeName(TypeElement superTypeElement) {
        TypeName superTypeName;
        if (superTypeElement != null) {
            superTypeName = ClassName.bestGuess(superTypeElement.getQualifiedName() + CommonConstants.BINDER_NAME_SUFFIX);
        } else {
            superTypeName = ARG_BINDER_CLASS;
        }
        superTypeName = ParameterizedTypeName.get((ClassName) superTypeName, TypeVariableName.get("T"));
        return superTypeName;
    }

    /**
     * Get the type in the bundle.
     */
    private String getBundleMethodType(Element element) {
        TypeMirror typeMirror = element.asType();
        String type = bundleMethodTypes.get(typeMirror.toString());
        if (type == null) {
            type = getBundleMethodType(typeMirror);
            bundleMethodTypes.put(typeMirror.toString(), type);
        }
        return type;
    }

    /**
     * Get the type in the bundle.
     */
    private String getBundleMethodType(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);
        if (typeName.isPrimitive()) {
            return ProcessorUtils.toFirstLetterUpperCase(typeName.toString());
        }
        if (typeName.toString().equals(STRING)) {
            return "String";
        }
        if (typeUtil.isSubtype(typeMirror, parcelableType)) {
            return "Parcelable";
        }
        if (typeUtil.isSubtype(typeMirror, serializableType)) {
            return "Serializable";
        }
        return null;
    }

    private boolean isFragment(Element element) {
        return typeUtil.isSubtype(element.asType(), fragmentType)
                || typeUtil.isSubtype(element.asType(), v4FragmentType);
    }

    private boolean isActivity(Element element) {
        return typeUtil.isSubtype(element.asType(), activityType);
    }

    private boolean isService(Element element) {
        return typeUtil.isSubtype(element.asType(), serviceType);
    }

    /**
     * Whether it's Parcelable[].
     */
    private boolean isParcelableArray(TypeMirror typeMirror) {
        return TypeKind.ARRAY.equals(typeMirror.getKind()) && typeUtil.isSubtype(((ArrayType) typeMirror).getComponentType(), parcelableType);
    }

    /**
     * Check whether the bind target is legal.
     */
    private void checkTarget(TypeElement targetElement) {
        if (!(isActivity(targetElement) || isService(targetElement) || isFragment(targetElement))) {
            ProcessorUtils.error("The bind target must be activity、service or fragment.[%s]", targetElement.getQualifiedName());
        }

        if (targetElement.getNestingKind() != NestingKind.TOP_LEVEL) {
            ProcessorUtils.error("The bind target must be top level class.[%s]", targetElement.getQualifiedName());
        }

        String qualifiedName = targetElement.getQualifiedName().toString();
        if (CommonUtils.isFrameworkPackage(qualifiedName)) {
            ProcessorUtils.error("The bind target incorrectly in Android or Java framework package. [%s]", qualifiedName);
        }
    }
}
