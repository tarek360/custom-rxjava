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

package custom.reactivex.internal.operators.observable;

import custom.reactivex.*;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.functions.BiFunction;
import custom.reactivex.internal.disposables.DisposableHelper;
import custom.reactivex.internal.functions.ObjectHelper;

public final class ObservableScan<T> extends AbstractObservableWithUpstream<T, T> {
    final BiFunction<T, T, T> accumulator;
    public ObservableScan(ObservableSource<T> source, BiFunction<T, T, T> accumulator) {
        super(source);
        this.accumulator = accumulator;
    }

    @Override
    public void subscribeActual(Observer<? super T> t) {
        source.subscribe(new ScanObserver<T>(t, accumulator));
    }

    static final class ScanObserver<T> implements Observer<T>, Disposable {
        final Observer<? super T> actual;
        final BiFunction<T, T, T> accumulator;

        Disposable s;

        T value;

        ScanObserver(Observer<? super T> actual, BiFunction<T, T, T> accumulator) {
            this.actual = actual;
            this.accumulator = accumulator;
        }

        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
            }
        }


        @Override
        public void dispose() {
            s.dispose();
        }

        @Override
        public boolean isDisposed() {
            return s.isDisposed();
        }


        @Override
        public void onNext(T t) {
            final Observer<? super T> a = actual;
            T v = value;
            if (v == null) {
                value = t;
                a.onNext(t);
            } else {
                T u;

                try {
                    u = ObjectHelper.requireNonNull(accumulator.apply(v, t), "The value returned by the accumulator is null");
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    s.dispose();
                    a.onError(e);
                    return;
                }

                value = u;
                a.onNext(u);
            }
        }

        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
