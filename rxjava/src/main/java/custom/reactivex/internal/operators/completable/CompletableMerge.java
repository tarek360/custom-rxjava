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

package custom.reactivex.internal.operators.completable;

import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import custom.reactivex.*;
import custom.reactivex.disposables.*;
import custom.reactivex.internal.disposables.DisposableHelper;
import custom.reactivex.internal.subscriptions.SubscriptionHelper;
import custom.reactivex.internal.util.*;
import custom.reactivex.plugins.RxJavaPlugins;

public final class CompletableMerge extends Completable {
    final Publisher<? extends CompletableSource> source;
    final int maxConcurrency;
    final boolean delayErrors;

    public CompletableMerge(Publisher<? extends CompletableSource> source, int maxConcurrency, boolean delayErrors) {
        this.source = source;
        this.maxConcurrency = maxConcurrency;
        this.delayErrors = delayErrors;
    }

    @Override
    public void subscribeActual(CompletableObserver s) {
        CompletableMergeSubscriber parent = new CompletableMergeSubscriber(s, maxConcurrency, delayErrors);
        source.subscribe(parent);
    }

    static final class CompletableMergeSubscriber
    extends AtomicInteger
    implements Subscriber<CompletableSource>, Disposable {

        private static final long serialVersionUID = -2108443387387077490L;

        final CompletableObserver actual;
        final int maxConcurrency;
        final boolean delayErrors;

        final AtomicThrowable error;

        final CompositeDisposable set;

        Subscription s;

        CompletableMergeSubscriber(CompletableObserver actual, int maxConcurrency, boolean delayErrors) {
            this.actual = actual;
            this.maxConcurrency = maxConcurrency;
            this.delayErrors = delayErrors;
            this.set = new CompositeDisposable();
            this.error = new AtomicThrowable();
            lazySet(1);
        }

        @Override
        public void dispose() {
            s.cancel();
            set.dispose();
        }

        @Override
        public boolean isDisposed() {
            return set.isDisposed();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
                if (maxConcurrency == Integer.MAX_VALUE) {
                    s.request(Long.MAX_VALUE);
                } else {
                    s.request(maxConcurrency);
                }
            }
        }

        @Override
        public void onNext(CompletableSource t) {
            getAndIncrement();

            MergeInnerObserver inner = new MergeInnerObserver();
            set.add(inner);
            t.subscribe(inner);
        }

        @Override
        public void onError(Throwable t) {
            if (!delayErrors) {
                set.dispose();

                if (error.addThrowable(t)) {
                    if (getAndSet(0) > 0) {
                        actual.onError(error.terminate());
                    }
                } else {
                    RxJavaPlugins.onError(t);
                }
            } else {
                if (error.addThrowable(t)) {
                    if (decrementAndGet() == 0) {
                        actual.onError(error.terminate());
                    }
                } else {
                    RxJavaPlugins.onError(t);
                }
            }
        }

        @Override
        public void onComplete() {
            if (decrementAndGet() == 0) {
                Throwable ex = error.get();
                if (ex != null) {
                    actual.onError(error.terminate());
                } else {
                    actual.onComplete();
                }
            }
        }

        void innerError(MergeInnerObserver inner, Throwable t) {
            set.delete(inner);
            if (!delayErrors) {
                s.cancel();
                set.dispose();

                if (error.addThrowable(t)) {
                    if (getAndSet(0) > 0) {
                        actual.onError(error.terminate());
                    }
                } else {
                    RxJavaPlugins.onError(t);
                }
            } else {
                if (error.addThrowable(t)) {
                    if (decrementAndGet() == 0) {
                        actual.onError(error.terminate());
                    } else {
                        if (maxConcurrency != Integer.MAX_VALUE) {
                            s.request(1);
                        }
                    }
                } else {
                    RxJavaPlugins.onError(t);
                }
            }
        }

        void innerComplete(MergeInnerObserver inner) {
            set.delete(inner);
            if (decrementAndGet() == 0) {
                Throwable ex = error.get();
                if (ex != null) {
                    actual.onError(ex);
                } else {
                    actual.onComplete();
                }
            } else {
                if (maxConcurrency != Integer.MAX_VALUE) {
                    s.request(1);
                }
            }
        }

        final class MergeInnerObserver
        extends AtomicReference<Disposable>
        implements CompletableObserver, Disposable {
            private static final long serialVersionUID = 251330541679988317L;

            @Override
            public void onSubscribe(Disposable d) {
                DisposableHelper.setOnce(this, d);
            }

            @Override
            public void onError(Throwable e) {
                innerError(this, e);
            }

            @Override
            public void onComplete() {
                innerComplete(this);
            }

            @Override
            public boolean isDisposed() {
                return DisposableHelper.isDisposed(get());
            }

            @Override
            public void dispose() {
                DisposableHelper.dispose(this);
            }
        }
    }
}
