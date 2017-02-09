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

package custom.reactivex.internal.operators.single;

import java.util.concurrent.atomic.AtomicReference;

import custom.reactivex.*;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.functions.Function;
import custom.reactivex.internal.disposables.DisposableHelper;
import custom.reactivex.internal.functions.ObjectHelper;

/**
 * Maps the success value of the source SingleSource into a Completable.
 * @param <T>
 */
public final class SingleFlatMapCompletable<T> extends Completable {

    final SingleSource<T> source;

    final Function<? super T, ? extends CompletableSource> mapper;

    public SingleFlatMapCompletable(SingleSource<T> source, Function<? super T, ? extends CompletableSource> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(CompletableObserver s) {
        FlatMapCompletableObserver<T> parent = new FlatMapCompletableObserver<T>(s, mapper);
        s.onSubscribe(parent);
        source.subscribe(parent);
    }

    static final class FlatMapCompletableObserver<T>
    extends AtomicReference<Disposable>
    implements SingleObserver<T>, CompletableObserver, Disposable {

        private static final long serialVersionUID = -2177128922851101253L;

        final CompletableObserver actual;

        final Function<? super T, ? extends CompletableSource> mapper;

        FlatMapCompletableObserver(CompletableObserver actual,
                Function<? super T, ? extends CompletableSource> mapper) {
            this.actual = actual;
            this.mapper = mapper;
        }

        @Override
        public void dispose() {
            DisposableHelper.dispose(this);
        }

        @Override
        public boolean isDisposed() {
            return DisposableHelper.isDisposed(get());
        }

        @Override
        public void onSubscribe(Disposable d) {
            DisposableHelper.replace(this, d);
        }

        @Override
        public void onSuccess(T value) {
            CompletableSource cs;

            try {
                cs = ObjectHelper.requireNonNull(mapper.apply(value), "The mapper returned a null CompletableSource");
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                onError(ex);
                return;
            }

            cs.subscribe(this);
        }

        @Override
        public void onError(Throwable e) {
            actual.onError(e);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}