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

import org.reactivestreams.*;

import custom.reactivex.*;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.functions.BiFunction;
import custom.reactivex.internal.functions.ObjectHelper;
import custom.reactivex.internal.fuseable.*;
import custom.reactivex.internal.subscriptions.SubscriptionHelper;
import custom.reactivex.plugins.RxJavaPlugins;

/**
 * Reduce a Flowable into a single value exposed as Single or signal NoSuchElementException.
 *
 * @param <T> the value type
 */
public final class FlowableReduceMaybe<T>
extends Maybe<T>
implements HasUpstreamPublisher<T>, FuseToFlowable<T> {

    final Flowable<T> source;

    final BiFunction<T, T, T> reducer;

    public FlowableReduceMaybe(Flowable<T> source, BiFunction<T, T, T> reducer) {
        this.source = source;
        this.reducer = reducer;
    }

    @Override
    public Publisher<T> source() {
        return source;
    }

    @Override
    public Flowable<T> fuseToFlowable() {
        return RxJavaPlugins.onAssembly(new FlowableReduce<T>(source, reducer));
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super T> observer) {
        source.subscribe(new ReduceSubscriber<T>(observer, reducer));
    }

    static final class ReduceSubscriber<T> implements Subscriber<T>, Disposable {
        final MaybeObserver<? super T> actual;

        final BiFunction<T, T, T> reducer;

        T value;

        Subscription s;

        boolean done;

        ReduceSubscriber(MaybeObserver<? super T> actual, BiFunction<T, T, T> reducer) {
            this.actual = actual;
            this.reducer = reducer;
        }

        @Override
        public void dispose() {
            s.cancel();
            done = true;
        }

        @Override
        public boolean isDisposed() {
            return done;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;

                actual.onSubscribe(this);

                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            T v = value;
            if (v == null) {
                value = t;
            } else {
                try {
                    value = ObjectHelper.requireNonNull(reducer.apply(v, t), "The reducer returned a null value");
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    s.cancel();
                    onError(ex);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            actual.onError(t);
         }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            T v = value;
            if (v != null) {
//                value = null;
                actual.onSuccess(v);
            } else {
                actual.onComplete();
            }
        }


    }
}
