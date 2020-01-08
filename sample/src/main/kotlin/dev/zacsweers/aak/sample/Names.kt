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
package dev.zacsweers.aak.sample

import dev.zacsweers.aak.annotation.AutoAnnotation
import kotlin.reflect.KClass

annotation class Named(val name: String)
annotation class NestedAnnotation(val name: String, val nested: Named)
annotation class GenericAnnotation<T : Any>(val generic: KClass<T>)

object Names {

  @AutoAnnotation fun nameAnnotation(name: String): Named {
    return aak_names_nameAnnotation(name)
  }

  @AutoAnnotation fun nestedAnnotation(name: String, named: Named): NestedAnnotation {
    return aak_names_nestedAnnotation(name, named)
  }

  inline fun <reified T : Any> genericAnnotation(): GenericAnnotation<T> {
    return genericAnnotation(T::class)
  }

  // Can't do reified because they're synthetic and therefore not visible from apt
  @AutoAnnotation fun <T : Any> genericAnnotation(target: KClass<T>): GenericAnnotation<T> {
    return aak_names_genericAnnotation(target)
  }

//  fun nameAnnotationDirect(name: String): Named {
//    return Named::class.aakInstance(name)
//  }
}

// fun aak_names_nameAnnotation(name: String): Named {
//  val handler = InvocationHandler { proxy, method, args ->
//    when (method.name) {
//      "name" -> name
//      else -> null
//    }
//  }
//  return Proxy.newProxyInstance(Named::class.java.classLoader, arrayOf(Named::class.java), handler) as Named
// }
