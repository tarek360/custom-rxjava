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

package custom.reactivex.internal.operators.maybe;

import java.util.Iterator;

import custom.reactivex.*;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.functions.Function;
import custom.reactivex.internal.disposables.DisposableHelper;
import custom.reactivex.internal.functions.ObjectHelper;
import custom.reactivex.internal.observers.BasicQueueDisposable;

/**
 * Maps a success value into an Iterable and streams it back as a Flowable.
 *
 * @param <T> the source value type
 * @param <R> the element type of the Iterable
 */
public final class MaybeFlatMapIterableObservable<T, R> extends Observable<R> {

    final MaybeSource<T> source;

    final Function<? super T, ? extends Iterable<? extends R>> mapper;

    public MaybeFlatMapIterableObservable(MaybeSource<T> source,
            Function<? super T, ? extends Iterable<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(Observer<? super R> s) {
        source.subscribe(new FlatMapIterableObserver<T, R>(s, mapper));
    }

    static final class FlatMapIterableObserver<T, R>
    extends BasicQueueDisposable<R>
    implements MaybeObserver<T> {

        final Observer<? super R> actual;

        final Function<? super T, ? extends Iterable<? extends R>> mapper;

        Disposable d;

        volatile Iterator<? extends R> it;

        volatile boolean cancelled;

        boolean outputFused;

        FlatMapIterableObserver(Observer<? super R> actual,
                Function<? super T, ? extends Iterable<? extends R>> mapper) {
            this.actual = actual;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            if (DisposableHelper.validate(this.d, d)) {
                this.d = d;

                actual.onSubscribe(this);
            }
        }

        @Override
        public void onSuccess(T value) {
            Observer<? super R> a = actual;

            Iterator<? extends R> iter;
            boolean has;
            try {
                iter = mapper.apply(value).iterator();

                has = iter.hasNext();
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                a.onError(ex);
                return;
            }

            if (!has) {
                a.onComplete();
                return;
            }

            this.it = iter;

            if (outputFused && iter != null) {
                a.onNext(null);
                a.onComplete();
                return;
            }

            for (;;) {
                if (cancelled) {
                    return;
                }

                R v;

                try {
                    v = iter.next();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    a.onError(ex);
                    return;
                }

                a.onNext(v);

                if (cancelled) {
                    return;
                }


                boolean b;

                try {
                    b = iter.hasNext();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    a.onError(ex);
                    return;
                }

                if (!b) {
                    a.onComplete();
                    return;
                }
            }
        }

        @Override
        public void onError(Throwable e) {
            d = DisposableHelper.DISPOSED;
            actual.onError(e);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }

        @Override
        public void dispose() {
            cancelled = true;
            d.dispose();
            d = DisposableHelper.DISPOSED;
        }

        @Override
        public boolean isDisposed() {
            return cancelled;
        }

        @Override
        public int requestFusion(int mode) {
            if ((mode & ASYNC) != 0) {
                outputFused = true;
                return ASYNC;
            }
            return NONE;
        }

        @Override
        public void clear() {
            it = null;
        }

        @Override
        public boolean isEmpty() {
            return it == null;
        }

        @Override
        public R poll() throws Exception {
            Iterator<? extends R> iter = it;

            if (iter != null) {
                R v = ObjectHelper.requireNonNull(iter.next(), "The iterator returned a null value");
                if (!iter.hasNext()) {
                    it = null;
                }
                return v;
            }
            return null;
        }

    }
}