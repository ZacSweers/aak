/*
 * Copyright (C) 2020. Zac Sweers
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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NamesTest {

  @Test
  fun simpleString() {
    val namedAnnotation = Names.nameAnnotation("taco")
    assertThat(namedAnnotation.name).isEqualTo("taco")
    assertThat(namedAnnotation.hashCode()).isEqualTo(0)
    assertThat(namedAnnotation.toString()).isEqualTo("AakProxy_Names#nameAnnotation")
    assertThat(namedAnnotation == namedAnnotation).isFalse()
  }

  @Test
  fun nested() {
    val nestedAnnotation = Names.nestedAnnotation("taco", Names.nameAnnotation("nestedTaco"))
    assertThat(nestedAnnotation.name).isEqualTo("taco")
    assertThat(nestedAnnotation.hashCode()).isEqualTo(0)
    assertThat(nestedAnnotation.toString()).isEqualTo("AakProxy_Names#nestedAnnotation")
    assertThat(nestedAnnotation == nestedAnnotation).isFalse()

    val namedAnnotation = nestedAnnotation.nested
    assertThat(namedAnnotation.name).isEqualTo("nestedTaco")
    assertThat(namedAnnotation.hashCode()).isEqualTo(0)
    assertThat(namedAnnotation.toString()).isEqualTo("AakProxy_Names#nameAnnotation")
    assertThat(namedAnnotation == namedAnnotation).isFalse()
  }

  @Test
  fun generic() {
    val genericAnnotation = Names.genericAnnotation<String>()
    // TODO this access results in a ClassCastException at runtime
    //  kotlin.jvm.internal.ClassReference cannot be cast to java.lang.Class
    assertThat(genericAnnotation.generic).isEqualTo(String::class)
    assertThat(genericAnnotation.hashCode()).isEqualTo(0)
    assertThat(genericAnnotation.toString()).isEqualTo("AakProxy_Names#genericAnnotation")
    assertThat(genericAnnotation == genericAnnotation).isFalse()
  }
}
