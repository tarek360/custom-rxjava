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

package custom.reactivex.internal.observers;

import java.util.concurrent.atomic.AtomicReference;

import custom.reactivex.*;
import custom.reactivex.disposables.Disposable;
import custom.reactivex.exceptions.*;
import custom.reactivex.functions.Consumer;
import custom.reactivex.internal.disposables.DisposableHelper;
import custom.reactivex.plugins.RxJavaPlugins;

public final class ToNotificationObserver<T>
extends AtomicReference<Disposable>
implements Observer<T>, Disposable {
    private static final long serialVersionUID = -7420197867343208289L;

    final Consumer<? super Notification<Object>> consumer;

    public ToNotificationObserver(Consumer<? super Notification<Object>> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onSubscribe(Disposable s) {
        DisposableHelper.setOnce(this, s);
    }

    @Override
    public void onNext(T t) {
        if (t == null) {
            get().dispose();
            onError(new NullPointerException("onNext called with null. Null values are generally not allowed in 2.x operators and sources."));
        } else {
            try {
                consumer.accept(Notification.<Object>createOnNext(t));
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                get().dispose();
                onError(ex);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        try {
            consumer.accept(Notification.<Object>createOnError(t));
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            RxJavaPlugins.onError(new CompositeException(t, ex));
        }
    }

    @Override
    public void onComplete() {
        try {
            consumer.accept(Notification.createOnComplete());
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            RxJavaPlugins.onError(ex);
        }
    }

    @Override
    public void dispose() {
        DisposableHelper.dispose(this);
    }

    @Override
    public boolean isDisposed() {
        return DisposableHelper.isDisposed(get());
    }
}
