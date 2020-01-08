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
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
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
      val generatedFunctions = functions.mapNotNull { processFunctionElement(typeElement, it) }

      // TODO if this is a facade we need to strip "Kt" suffix from the type element name
      FileSpec.builder(MoreElements.getPackage(typeElement).qualifiedName.toString(), "${typeElement.simpleName}Annotations")
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

  private fun processFunctionElement(type: TypeElement, method: ExecutableElement): FunSpec? {
    //
    // Given
    //
    // @AutoAnnotation fun foo(<params>): <Annotation> {}
    //
    // Generate
    //
    // fun aak_names_foo(<all annotations>) = A
    //
    val returnType = method.returnType
    if (returnType.kind != TypeKind.DECLARED) {
      processingEnv.messager.printMessage(ERROR, "Return type is not an annotation!", method)
      return null
    }
    val returnElement = (returnType as DeclaredType).asElement() as TypeElement
    if (returnElement.kind != ElementKind.ANNOTATION_TYPE) {
      processingEnv.messager.printMessage(ERROR, "Return type is not an annotation!", method)
      return null
    }
    val metadata = returnElement.getAnnotation(Metadata::class.java)

    // TODO capture whether or not methods have defaults
    val allocator = NameAllocator()
    val attributes = if (metadata == null) {
      javaAnnotationApi(returnElement)
    } else {
      kotlinAnnotationApi(metadata)
    }.mapKeys { allocator.newName(it.key) }

    val targetClass = type.asClassName()
    val handlerVar = allocator.newName("handler")
    val methodParam = allocator.newName("method")
    val methodName = allocator.newName("methodName")
    val returnClassName = returnElement.asClassName()
    val returnTypeName = returnType.asTypeName()
    return FunSpec.builder("aak_${targetClass.simpleNames.joinToString("_") { it.decapitalize() }}_${method.simpleName}")
        .addOriginatingElement(type)
        .apply {
          if (returnTypeName is ParameterizedTypeName) {
            returnTypeName.typeArguments
                .filterIsInstanceTo<TypeVariableName, MutableSet<TypeVariableName>>(mutableSetOf())
                .forEach {
                  // TODO Pull these from the actual funspec rather than add an Any
                  val finalVar = if (ANY in it.bounds) {
                    it
                  } else {
                    it.copy(bounds = it.bounds + ANY)
                  }
                  addTypeVariable(finalVar)
                }
          }
        }
        .addParameters(attributes.map { (name, type) ->
          ParameterSpec.builder(name, type).build()
        })
        .returns(returnTypeName)
        // TODO indent properly rather than the custom leading spacing
        .addStatement("val %L = %T { _, %L, _ ->⇥", handlerVar, InvocationHandler::class, methodParam)
        .addCode("when (val %L = %L.name) {⇥", methodName, methodParam)
        .apply {
          for ((name, value) in attributes.entries) {
            val possibleSuffix = if (value == KClass::class.asClassName()) ".java" else ""
            addStatement("%1S -> %1L$possibleSuffix", name)
          }
        }
        .addStatement("%S -> %S", "toString", "AakProxy_${targetClass.simpleNames.joinToString("_") { it.capitalize() }}#${method.simpleName}")
        // TODO proper hashcode/equals?
//        .addStatement("%S -> %T.hash(%L)", "hashCode", Objects::class, attributes.keys.map { CodeBlock.of("%L", it) }.joinToCode(", "))
        .addStatement("%S -> 0", "hashCode")
        .addStatement("%S -> false", "equals")
        .addStatement("else -> error(\"Unrecognized method call: ${'$'}$methodName\")")
        .addStatement("⇤}")
        .addStatement("⇤}")
        .apply {
          if (returnTypeName is ParameterizedTypeName) {
            addStatement("@%T(%S)", Suppress::class, "UNCHECKED_CAST")
          }
        }
        .addStatement("return %1T.newProxyInstance(%2T::class.java.classLoader, arrayOf(%2T::class.java), %3L) as %4T", Proxy::class.asClassName(), returnClassName, handlerVar, returnTypeName)
        .build()
  }

  private fun javaAnnotationApi(type: TypeElement): Map<String, TypeName> {
    return ElementFilter.methodsIn(type.enclosedElements)
        .associate { it.simpleName.toString() to it.returnType.asTypeName() }
  }

  private fun kotlinAnnotationApi(metadata: Metadata): Map<String, TypeName> {
    val kotlinApi = metadata.toImmutableKmClass().toTypeSpec(classInspector)
    return kotlinApi.propertySpecs.associate {
      it.name to it.type
    }
  }
}
