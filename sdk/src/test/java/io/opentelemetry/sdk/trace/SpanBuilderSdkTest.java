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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;

import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Link;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.Tracestate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SpanBuilderSdk}. */
@RunWith(JUnit4.class)
public class SpanBuilderSdkTest {
  private static final String SPAN_NAME = "span_name";
  private final SpanContext sampledSpanContext =
      SpanContext.create(
          new TraceId(1000, 1000),
          new SpanId(3000),
          TraceFlags.builder().setIsSampled(true).build(),
          Tracestate.getDefault());

  private final TracerSdkFactory tracerSdkFactory = TracerSdkFactory.create();
  private final TracerSdk tracerSdk = tracerSdkFactory.get("SpanBuilderSdkTest");

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void setSpanKind_null() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).setSpanKind(null);
  }

  @Test
  public void setParent_null() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).setParent((Span) null);
  }

  @Test
  public void setRemoteParent_null() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).setParent((SpanContext) null);
  }

  @Test
  public void addLink() {
    // Verify methods do not crash.
    Span.Builder spanBuilder = tracerSdk.spanBuilder(SPAN_NAME);
    spanBuilder.addLink(SpanData.Link.create(DefaultSpan.getInvalid().getContext()));
    spanBuilder.addLink(DefaultSpan.getInvalid().getContext());
    spanBuilder.addLink(
        DefaultSpan.getInvalid().getContext(), Collections.<String, AttributeValue>emptyMap());

    RecordEventsReadableSpan span = (RecordEventsReadableSpan) spanBuilder.startSpan();
    try {
      assertThat(span.getLinks()).hasSize(3);
    } finally {
      span.end();
    }
  }

  @Test
  public void truncateLink() {
    final int maxNumberOfLinks = 8;
    TraceConfig traceConfig =
        tracerSdkFactory
            .getActiveTraceConfig()
            .toBuilder()
            .setMaxNumberOfLinks(maxNumberOfLinks)
            .build();
    tracerSdkFactory.updateActiveTraceConfig(traceConfig);
    // Verify methods do not crash.
    Span.Builder spanBuilder = tracerSdk.spanBuilder(SPAN_NAME);
    for (int i = 0; i < 2 * maxNumberOfLinks; i++) {
      spanBuilder.addLink(sampledSpanContext);
    }
    RecordEventsReadableSpan span = (RecordEventsReadableSpan) spanBuilder.startSpan();
    try {
      assertThat(span.getDroppedLinksCount()).isEqualTo(maxNumberOfLinks);
      assertThat(span.getLinks().size()).isEqualTo(maxNumberOfLinks);
      for (int i = 0; i < maxNumberOfLinks; i++) {
        assertThat(span.getLinks().get(i)).isEqualTo(SpanData.Link.create(sampledSpanContext));
      }
    } finally {
      span.end();
      tracerSdkFactory.updateActiveTraceConfig(TraceConfig.getDefault());
    }
  }

  @Test
  public void addLink_null() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).addLink((Link) null);
  }

  @Test
  public void addLinkSpanContext_null() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).addLink((SpanContext) null);
  }

  @Test
  public void addLinkSpanContextAttributes_nullContext() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).addLink(null, Collections.<String, AttributeValue>emptyMap());
  }

  @Test
  public void addLinkSpanContextAttributes_nullAttributes() {
    thrown.expect(NullPointerException.class);
    tracerSdk.spanBuilder(SPAN_NAME).addLink(DefaultSpan.getInvalid().getContext(), null);
  }

  @Test
  public void recordEvents_default() {
    Span span = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    try {
      assertThat(span.isRecording()).isTrue();
    } finally {
      span.end();
    }
  }

  @Test
  public void kind_default() {
    RecordEventsReadableSpan span =
        (RecordEventsReadableSpan) tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    try {
      assertThat(span.getKind()).isEqualTo(Kind.INTERNAL);
    } finally {
      span.end();
    }
  }

  @Test
  public void kind() {
    RecordEventsReadableSpan span =
        (RecordEventsReadableSpan)
            tracerSdk.spanBuilder(SPAN_NAME).setSpanKind(Kind.CONSUMER).startSpan();
    try {
      assertThat(span.getKind()).isEqualTo(Kind.CONSUMER);
    } finally {
      span.end();
    }
  }

  @Test
  public void sampler() {
    Span span =
        TestUtils.startSpanWithSampler(tracerSdkFactory, tracerSdk, SPAN_NAME, Samplers.alwaysOff())
            .startSpan();
    try {
      assertThat(span.getContext().getTraceFlags().isSampled()).isFalse();
    } finally {
      span.end();
    }
  }

  @Test
  public void sampler_decisionAttributes() {
    RecordEventsReadableSpan span =
        (RecordEventsReadableSpan)
            TestUtils.startSpanWithSampler(
                    tracerSdkFactory,
                    tracerSdk,
                    SPAN_NAME,
                    new Sampler() {
                      @Override
                      public Decision shouldSample(
                          @Nullable SpanContext parentContext,
                          TraceId traceId,
                          SpanId spanId,
                          String name,
                          List<Link> parentLinks) {
                        return new Decision() {
                          @Override
                          public boolean isSampled() {
                            return true;
                          }

                          @Override
                          public Map<String, AttributeValue> attributes() {
                            Map<String, AttributeValue> attributes = new LinkedHashMap<>();
                            attributes.put(
                                "sampler-attribute", AttributeValue.stringAttributeValue("bar"));
                            return attributes;
                          }
                        };
                      }

                      @Override
                      public String getDescription() {
                        return "test sampler";
                      }
                    })
                .startSpan();
    try {
      assertThat(span.getContext().getTraceFlags().isSampled()).isTrue();
      assertThat(span.getAttributes()).containsKey("sampler-attribute");
    } finally {
      span.end();
    }
  }

  @Test
  public void sampledViaParentLinks() {
    RecordEventsReadableSpan span =
        (RecordEventsReadableSpan)
            TestUtils.startSpanWithSampler(
                    tracerSdkFactory, tracerSdk, SPAN_NAME, Samplers.probability(0.0))
                .addLink(sampledSpanContext)
                .startSpan();
    try {
      assertThat(span.getContext().getTraceFlags().isSampled()).isTrue();
    } finally {
      if (span != null) {
        span.end();
      }
    }
  }

  @Test
  public void noParent() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    Scope scope = tracerSdk.withSpan(parent);
    try {
      Span span = tracerSdk.spanBuilder(SPAN_NAME).setNoParent().startSpan();
      try {
        assertThat(span.getContext().getTraceId()).isNotEqualTo(parent.getContext().getTraceId());

        Span spanNoParent =
            tracerSdk
                .spanBuilder(SPAN_NAME)
                .setNoParent()
                .setParent(parent)
                .setNoParent()
                .startSpan();
        try {
          assertThat(span.getContext().getTraceId()).isNotEqualTo(parent.getContext().getTraceId());
        } finally {
          spanNoParent.end();
        }
      } finally {
        span.end();
      }
    } finally {
      scope.close();
      parent.end();
    }
  }

  @Test
  public void noParent_override() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    try {
      RecordEventsReadableSpan span =
          (RecordEventsReadableSpan)
              tracerSdk.spanBuilder(SPAN_NAME).setNoParent().setParent(parent).startSpan();
      try {
        assertThat(span.getContext().getTraceId()).isEqualTo(parent.getContext().getTraceId());
        assertThat(span.getParentSpanId()).isEqualTo(parent.getContext().getSpanId());

        RecordEventsReadableSpan span2 =
            (RecordEventsReadableSpan)
                tracerSdk
                    .spanBuilder(SPAN_NAME)
                    .setNoParent()
                    .setParent(parent.getContext())
                    .startSpan();
        try {
          assertThat(span2.getContext().getTraceId()).isEqualTo(parent.getContext().getTraceId());
        } finally {
          span2.end();
        }
      } finally {
        span.end();
      }
    } finally {
      parent.end();
    }
  }

  @Test
  public void overrideNoParent_remoteParent() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    try {

      RecordEventsReadableSpan span =
          (RecordEventsReadableSpan)
              tracerSdk
                  .spanBuilder(SPAN_NAME)
                  .setNoParent()
                  .setParent(parent.getContext())
                  .startSpan();
      try {
        assertThat(span.getContext().getTraceId()).isEqualTo(parent.getContext().getTraceId());
        assertThat(span.getParentSpanId()).isEqualTo(parent.getContext().getSpanId());
      } finally {
        span.end();
      }
    } finally {
      parent.end();
    }
  }

  @Test
  public void parentCurrentSpan() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    Scope scope = tracerSdk.withSpan(parent);
    try {
      RecordEventsReadableSpan span =
          (RecordEventsReadableSpan) tracerSdk.spanBuilder(SPAN_NAME).startSpan();
      try {
        assertThat(span.getContext().getTraceId()).isEqualTo(parent.getContext().getTraceId());
        assertThat(span.getParentSpanId()).isEqualTo(parent.getContext().getSpanId());
      } finally {
        span.end();
      }
    } finally {
      scope.close();
      parent.end();
    }
  }

  @Test
  public void parent_invalidContext() {
    Span parent = DefaultSpan.getInvalid();

    RecordEventsReadableSpan span =
        (RecordEventsReadableSpan)
            tracerSdk.spanBuilder(SPAN_NAME).setParent(parent.getContext()).startSpan();
    try {
      assertThat(span.getContext().getTraceId()).isNotEqualTo(parent.getContext().getTraceId());
      assertFalse(span.getParentSpanId().isValid());
    } finally {
      span.end();
    }
  }

  @Test
  public void parent_timestampConverter() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    try {
      RecordEventsReadableSpan span =
          (RecordEventsReadableSpan) tracerSdk.spanBuilder(SPAN_NAME).setParent(parent).startSpan();

      assertThat(span.getClock()).isEqualTo(((RecordEventsReadableSpan) parent).getClock());
    } finally {
      parent.end();
    }
  }

  @Test
  public void parentCurrentSpan_timestampConverter() {
    Span parent = tracerSdk.spanBuilder(SPAN_NAME).startSpan();
    Scope scope = tracerSdk.withSpan(parent);
    try {
      RecordEventsReadableSpan span =
          (RecordEventsReadableSpan) tracerSdk.spanBuilder(SPAN_NAME).startSpan();

      assertThat(span.getClock()).isEqualTo(((RecordEventsReadableSpan) parent).getClock());
    } finally {
      scope.close();
      parent.end();
    }
  }

  @Test
  public void startTimestamp_null() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Negative startTimestamp");
    tracerSdk.spanBuilder(SPAN_NAME).setStartTimestamp(-1);
  }
}
