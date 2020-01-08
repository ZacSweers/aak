AAK (Auto-Annotation-Kotlin)
============================

## 🚧 Under construction 🚧 ##

An annotation processor to generate `Proxy`-based helper methods for creating Kotlin annotations at
runtime. Based on Google's (TODO link) AutoAnnotation processor for Java.

### Example usage

Create a function annotated with `@AutoAnnotation` that returns the desired annotation type. A function
with the `aak_` prefix + any class names + the function name will be generated with all the annotation 
values as parameters.

```kotlin
annotation class Named(val name: String)

object Names {
  @AutoAnnotation fun nameAnnotation(name: String): Named {
    return aak_names_nameAnnotation(name)
  }
}
```

Will generate a function like this:

```kotlin
fun aak_names_nameAnnotation(name: String): Named {
  val handler = InvocationHandler { _, method, _ ->
    when (val methodName = method.name) {
        "name" -> name
        "toString" -> "AakProxy_Names#nameAnnotation"
        "hashCode" -> 0
        "equals" -> false
        else -> error("Unrecognized method call: $methodName")
    }
  }
  return Proxy.newProxyInstance(Named::class.java.classLoader, arrayOf(Named::class.java), handler)
      as Named
}
```

### TODO

- Extract core artifact for reuse in other annotation processors.
- Publish snapshots + first release.
- Allow annotating annotations directly to generate helper methods for them.

License
-------

    Copyright (C) 2020 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
