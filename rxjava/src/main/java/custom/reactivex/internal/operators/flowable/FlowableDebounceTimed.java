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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import custom.reactivex.Scheduler;
import custom.reactivex.Scheduler.Worker;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.MissingBackpressureException;
import custom.reactivex.internal.disposables.*;
import custom.reactivex.internal.subscriptions.SubscriptionHelper;
import custom.reactivex.internal.util.BackpressureHelper;
import custom.reactivex.plugins.RxJavaPlugins;
import custom.reactivex.subscribers.SerializedSubscriber;

public final class FlowableDebounceTimed<T> extends AbstractFlowableWithUpstream<T, T> {
    final long timeout;
    final TimeUnit unit;
    final Scheduler scheduler;

    public FlowableDebounceTimed(Publisher<T> source, long timeout, TimeUnit unit, Scheduler scheduler) {
        super(source);
        this.timeout = timeout;
        this.unit = unit;
        this.scheduler = scheduler;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        source.subscribe(new DebounceTimedSubscriber<T>(
                new SerializedSubscriber<T>(s),
                timeout, unit, scheduler.createWorker()));
    }

    static final class DebounceTimedSubscriber<T> extends AtomicLong
    implements Subscriber<T>, Subscription {

        private static final long serialVersionUID = -9102637559663639004L;
        final Subscriber<? super T> actual;
        final long timeout;
        final TimeUnit unit;
        final Scheduler.Worker worker;

        Subscription s;

        final SequentialDisposable timer = new SequentialDisposable();

        volatile long index;

        boolean done;

        DebounceTimedSubscriber(Subscriber<? super T> actual, long timeout, TimeUnit unit, Worker worker) {
            this.actual = actual;
            this.timeout = timeout;
            this.unit = unit;
            this.worker = worker;
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
            long idx = index + 1;
            index = idx;

            Disposable d = timer.get();
            if (d != null) {
                d.dispose();
            }

            DebounceEmitter<T> de = new DebounceEmitter<T>(t, idx, this);
            if (timer.replace(de)) {
                d = worker.schedule(de, timeout, unit);

                de.setResource(d);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            DisposableHelper.dispose(timer);
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;

            Disposable d = timer.get();
            if (!DisposableHelper.isDisposed(d)) {
                @SuppressWarnings("unchecked")
                DebounceEmitter<T> de = (DebounceEmitter<T>)d;
                if (de != null) {
                    de.emit();
                }
                DisposableHelper.dispose(timer);
                worker.dispose();
                actual.onComplete();
            }
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(this, n);
            }
        }

        @Override
        public void cancel() {
            DisposableHelper.dispose(timer);
            worker.dispose();
            s.cancel();
        }

        void emit(long idx, T t, DebounceEmitter<T> emitter) {
            if (idx == index) {
                long r = get();
                if (r != 0L) {
                    actual.onNext(t);
                    BackpressureHelper.produced(this, 1);

                    emitter.dispose();
                } else {
                    cancel();
                    actual.onError(new MissingBackpressureException("Could not deliver value due to lack of requests"));
                }
            }
        }
    }

    static final class DebounceEmitter<T> extends AtomicReference<Disposable> implements Runnable, Disposable {

        private static final long serialVersionUID = 6812032969491025141L;

        final T value;
        final long idx;
        final DebounceTimedSubscriber<T> parent;

        final AtomicBoolean once = new AtomicBoolean();


        DebounceEmitter(T value, long idx, DebounceTimedSubscriber<T> parent) {
            this.value = value;
            this.idx = idx;
            this.parent = parent;
        }

        @Override
        public void run() {
            emit();
        }

        void emit() {
            if (once.compareAndSet(false, true)) {
                parent.emit(idx, value, this);
            }
        }

        @Override
        public void dispose() {
            DisposableHelper.dispose(this);
        }

        @Override
        public boolean isDisposed() {
            return get() == DisposableHelper.DISPOSED;
        }

        public void setResource(Disposable d) {
            DisposableHelper.replace(this, d);
        }
    }
}