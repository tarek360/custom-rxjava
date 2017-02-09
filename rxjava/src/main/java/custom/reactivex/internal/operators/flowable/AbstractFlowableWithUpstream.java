/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package custom.reactivex.internal.operators.flowable;

import org.reactivestreams.Publisher;

import custom.reactivex.Flowable;
import custom.reactivex.internal.functions.ObjectHelper;
import custom.reactivex.internal.fuseable.HasUpstreamPublisher;

/**
 * Abstract base class for operators that take an upstream
 * source {@link Publisher}.
 *
 * @param <T> the upstream value type
 * @param <R> the output value type
 */
abstract class AbstractFlowableWithUpstream<T, R> extends Flowable<R> implements HasUpstreamPublisher<T> {

    /**
     * The upstream source Publisher.
     */
    protected final Publisher<T> source;

    /**
     * Constructs a FlowableSource wrapping the given non-null (verified)
     * source Publisher.
     * @param source the source (upstream) Publisher instance, not null (verified)
     */
    AbstractFlowableWithUpstream(Publisher<T> source) {
        this.source = ObjectHelper.requireNonNull(source, "source is null");
    }

    @Override
    public final Publisher<T> source() {
        return source;
    }
}
