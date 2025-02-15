/*
 * Copyright 2019, OpenTelemetry Authors
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

package io.opentelemetry.sdk.trace;

import com.google.auto.value.AutoValue;
import io.opentelemetry.internal.Utils;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Holds information about the instrumentation library specified when creating an instance of {@link
 * TracerSdk} using {@link TracerSdkFactory}.
 */
@AutoValue
@Immutable
public abstract class InstrumentationLibraryInfo {

  static final InstrumentationLibraryInfo EMPTY = create("", null);

  /**
   * Creates a new instance of {@link InstrumentationLibraryInfo}.
   *
   * @param name name of the instrumentation library (e.g., "io.opentelemetry.contrib.mongodb"),
   *     must not be null
   * @param version version of the instrumentation library (e.g., "semver:1.0.0"), might be null
   * @return the new instance
   */
  static InstrumentationLibraryInfo create(String name, @Nullable String version) {
    Utils.checkNotNull(name, "name");
    return new AutoValue_InstrumentationLibraryInfo(name, version);
  }

  public abstract String name();

  @Nullable
  public abstract String version();

  // Package protected ctor to avoid others to extend this class.
  InstrumentationLibraryInfo() {}
}
