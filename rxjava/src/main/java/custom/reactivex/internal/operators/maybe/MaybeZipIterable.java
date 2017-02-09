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

import java.util.Arrays;

import custom.reactivex.*;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.functions.Function;
import custom.reactivex.internal.disposables.EmptyDisposable;
import custom.reactivex.internal.operators.maybe.MaybeZipArray.ZipCoordinator;

public final class MaybeZipIterable<T, R> extends Maybe<R> {

    final Iterable<? extends MaybeSource<? extends T>> sources;

    final Function<? super Object[], ? extends R> zipper;

    public MaybeZipIterable(Iterable<? extends MaybeSource<? extends T>> sources, Function<? super Object[], ? extends R> zipper) {
        this.sources = sources;
        this.zipper = zipper;
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super R> observer) {
        @SuppressWarnings("unchecked")
        MaybeSource<? extends T>[] a = new MaybeSource[8];
        int n = 0;

        try {
            for (MaybeSource<? extends T> source : sources) {
                if (n == a.length) {
                    a = Arrays.copyOf(a, n + (n >> 2));
                }
                a[n++] = source;
            }
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            EmptyDisposable.error(ex, observer);
            return;
        }

        if (n == 0) {
            EmptyDisposable.complete(observer);
            return;
        }

        if (n == 1) {
            a[0].subscribe(new MaybeMap.MapMaybeObserver<T, R>(observer, new Function<T, R>() {
                @Override
                public R apply(T t) throws Exception {
                    return zipper.apply(new Object[] { t });
                }
            }));
            return;
        }

        ZipCoordinator<T, R> parent = new ZipCoordinator<T, R>(observer, n, zipper);

        observer.onSubscribe(parent);

        for (int i = 0; i < n; i++) {
            if (parent.isDisposed()) {
                return;
            }

            a[i].subscribe(parent.observers[i]);
        }
    }

}
