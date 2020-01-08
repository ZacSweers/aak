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
package dev.zacsweers.aak.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import kotlin.jvm.JvmField
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Stub for prototyping stuff right now
 */
@KotlinPoetMetadataPreview
class AakProcessorTest {
  @Rule
  @JvmField
  var temporaryFolder = TemporaryFolder()

  @Test
  fun testing() {
    val result = compile(kotlin("source.kt", """

        """))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
    return KotlinCompilation()
        .apply {
          workingDir = temporaryFolder.root
          annotationProcessors = listOf(AakProcessor())
          inheritClassPath = true
          sources = sourceFiles.asList()
          verbose = false
        }
  }

  private fun compile(vararg sourceFiles: SourceFile): KotlinCompilation.Result {
    return prepareCompilation(*sourceFiles).compile()
  }
}
