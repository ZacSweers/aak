/*
 * Copyright (C) 2019. Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UnstableApiUsage")

package dev.zacsweers.aak.compiler

import com.google.auto.common.MoreElements
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.classinspector.elements.ElementsClassInspector
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.specs.ClassInspector
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import com.squareup.kotlinpoet.tag
import dev.zacsweers.aak.annotation.AutoAnnotation
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import kotlin.reflect.KClass
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING

@KotlinPoetMetadataPreview
@AutoService(Processor::class)
@IncrementalAnnotationProcessor(ISOLATING)
class AakProcessor : AbstractProcessor() {

  private lateinit var classInspector: ClassInspector

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    classInspector = ElementsClassInspector.create(processingEnv.elementUtils,
        processingEnv.typeUtils)
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(AutoAnnotation::class.java.canonicalName)
  }

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val annotatedElements = annotations.flatMap(roundEnv::getElementsAnnotatedWith)

    // TODO
    //  - check valid return types
    val functionElements = annotatedElements.filterIsInstance<ExecutableElement>()
        .groupBy { it.enclosingElement as TypeElement }
        .mapKeys { (key, _) ->
          key.toTypeSpec(classInspector).toBuilder()
              .tag(key)
              .build()
        }
//        .mapValues { (type, value) ->
//          value.map { method ->
//            // TODO get these into messager instead
//            val methodSignature = method.jvmMethodSignature(processingEnv.typeUtils)
//            val funSpec = type.funSpecs.first {
//              val kmFunction = requireNotNull(it.tag<ImmutableKmFunction>()) {
//                "No KmFunction tagged!"
//              }
//              val signature = requireNotNull(kmFunction.signature) {
//                "No method signature found!"
//              }
//              signature.asString() == methodSignature
//            }
//            funSpec.toBuilder()
//                .tag(method)
//                .build()
//          }
//        }

    for ((type, functions) in functionElements) {
      val typeElement = type.tag<TypeElement>()!!
      val targetClass = typeElement.asClassName()
      val generatedFunctions = functions.mapNotNull { method ->
        val returnType = method.returnType
        if (returnType.kind != TypeKind.DECLARED) {
          processingEnv.messager.printMessage(ERROR, "Return type is not an annotation!", method)
          return@mapNotNull null
        }
        val returnElement = (returnType as DeclaredType).asElement() as TypeElement
        if (returnElement.kind != ElementKind.ANNOTATION_TYPE) {
          processingEnv.messager.printMessage(ERROR, "Return type is not an annotation!", method)
          return@mapNotNull null
        }

        val methodName = method.simpleName
        val name = "aak_${targetClass.simpleNames.joinToString(
            "_") { it.decapitalize() }}_$methodName"
        val toStringName = "AakProxy_${targetClass.simpleNames.joinToString(
            "_") { it.capitalize() }}#$methodName"
        createFunSpec(name, returnElement, toStringName)?.toBuilder()
            ?.addOriginatingElement(typeElement)
            ?.build()
      }

      // TODO if this is a facade we need to strip "Kt" suffix from the type element name
      FileSpec.builder(
          packageName = MoreElements.getPackage(typeElement).qualifiedName.toString(),
          fileName = "${typeElement.simpleName}Annotations"
      )
          .apply {
            for (function in generatedFunctions) {
              addFunction(function)
            }
          }
          .indent("  ")
          .build()
          .writeTo(processingEnv.filer)
    }

    val typeElements = annotatedElements.filterIsInstance<TypeElement>()

    return true
  }

  private fun createFunSpec(
    name: String,
    annotationType: TypeElement,
    toStringName: String = name
  ): FunSpec? {
    val metadata = annotationType.getAnnotation(Metadata::class.java)

    // TODO capture whether or not methods have defaults
    val annotationData = if (metadata == null) {
      javaAnnotationApi(annotationType)
    } else {
      kotlinAnnotationApi(MoreElements.getPackage(annotationType).toString(), metadata)
    }

    return createFunSpec(name, annotationData, toStringName)
  }

  private fun createFunSpec(
    name: String,
    annotationData: AnnotationData,
    toStringName: String = name
  ): FunSpec? {
    //
    // Given
    //
    // @AutoAnnotation fun foo(<params>): <Annotation> {}
    //
    // Generate
    //
    // fun aak_names_foo(<all annotations>) = A
    //
    val allocator = NameAllocator()
    val members = annotationData.members.map { it.copy(name = allocator.newName(it.name)) }

    val handlerVar = allocator.newName("handler")
    val methodParam = allocator.newName("method")
    val methodName = allocator.newName("methodName")
    val returnClassName = annotationData.type.rawType
    return FunSpec.builder(name)
        .apply {
          if (annotationData.type is ParameterizedTypeName) {
            addTypeVariables(annotationData.type.typeArguments.filterIsInstance<TypeVariableName>())
          }
        }
        .addParameters(members.map { (name, type, defaultValue) ->
          ParameterSpec.builder(name, type).build()
        })
        .returns(annotationData.type)
        // TODO indent properly rather than the custom leading spacing
        .addStatement("val %L = %T { _, %L, _ ->⇥", handlerVar, InvocationHandler::class,
            methodParam)
        .addStatement("when (val %L = %L.name) {⇥", methodName, methodParam)
        .apply {
          for ((attrName, value, _) in members) {
            val possibleSuffix = if (value.isKClassType()) ".java" else ""
            addStatement("%1S -> %1L$possibleSuffix", attrName)
          }
        }
        .addStatement("%S -> %S", "toString", toStringName)
        // TODO proper hashcode/equals?
//        .addStatement("%S -> %T.hash(%L)", "hashCode", Objects::class, attributes.keys.map { CodeBlock.of("%L", it) }.joinToCode(", "))
        .addStatement("%S -> 0", "hashCode")
        .addStatement("%S -> false", "equals")
        .addStatement("else -> error(\"Unrecognized method call: ${'$'}$methodName\")")
        .addStatement("⇤}")
        .addStatement("⇤}")
        .apply {
          if (annotationData.type is ParameterizedTypeName) {
            addStatement("@%T(%S)", Suppress::class, "UNCHECKED_CAST")
          }
        }
        .addStatement(
            "return %1T.newProxyInstance(%2T::class.java.classLoader, arrayOf(%2T::class.java), %3L) as %4T",
            Proxy::class.asClassName(), returnClassName, handlerVar, annotationData.type)
        .build()
  }

  private val TypeName.rawType: ClassName
    get() {
      return when (this) {
        is ClassName -> this
        is ParameterizedTypeName -> rawType
        else -> error("Must be a class name or parameterized class name")
      }
    }

  private fun TypeName.isKClassType(): Boolean {
    return when (this) {
      is ClassName -> this == KClass::class.asClassName()
      is ParameterizedTypeName -> rawType.isKClassType()
      else -> false
    }
  }

  private fun javaAnnotationApi(type: TypeElement): AnnotationData {
    return AnnotationData(
        type = type.asType().asTypeName(),
        members = ElementFilter.methodsIn(type.enclosedElements)
            .map {
              AnnotationAttribute(
                  it.simpleName.toString(),
                  it.returnType.asTypeName(),
                  null // TODO parse default values
              )
            }
    )
  }

  private fun kotlinAnnotationApi(packageName: String, metadata: Metadata): AnnotationData {
    val kotlinApi = metadata.toImmutableKmClass().toTypeSpec(classInspector)
    val resolvedPackageName = metadata.packageName.ifBlank { packageName }
    val className = ClassName(resolvedPackageName, kotlinApi.name!!)
    return AnnotationData(
        type = if (kotlinApi.typeVariables.isNotEmpty()) {
          className.parameterizedBy(kotlinApi.typeVariables)
        } else {
          className
        },
        members = kotlinApi.propertySpecs.map {
          AnnotationAttribute(
              it.name,
              it.type,
              null // TODO parse default values
          )
        }
    )
  }
}

data class AnnotationData(
  val type: TypeName,
  val members: List<AnnotationAttribute>
)

data class AnnotationAttribute(
  val name: String,
  val type: TypeName,
  val defaultValue: CodeBlock?
)
