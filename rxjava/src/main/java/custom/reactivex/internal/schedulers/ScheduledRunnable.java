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

package custom.reactivex.internal.schedulers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

import custom.reactivex.disposables.Disposable;
import custom.reactivex.internal.disposables.DisposableContainer;
import custom.reactivex.plugins.RxJavaPlugins;

public final class ScheduledRunnable extends AtomicReferenceArray<Object>
implements Runnable, Callable<Object>, Disposable {

    private static final long serialVersionUID = -6120223772001106981L;
    final Runnable actual;

    static final Object DISPOSED = new Object();

    static final Object DONE = new Object();

    static final int PARENT_INDEX = 0;
    static final int FUTURE_INDEX = 1;

    /**
     * Creates a ScheduledRunnable by wrapping the given action and setting
     * up the optional parent.
     * @param actual the runnable to wrap, not-null (not verified)
     * @param parent the parent tracking container or null if none
     */
    public ScheduledRunnable(Runnable actual, DisposableContainer parent) {
        super(2);
        this.actual = actual;
        this.lazySet(0, parent);
    }

    @Override
    public Object call() {
        // Being Callable saves an allocation in ThreadPoolExecutor
        run();
        return null;
    }

    @Override
    public void run() {
        try {
            try {
                actual.run();
            } catch (Throwable e) {
                // Exceptions.throwIfFatal(e); nowhere to go
                RxJavaPlugins.onError(e);
            }
        } finally {
            Object o = get(PARENT_INDEX);
            if (o != DISPOSED && o != null && compareAndSet(PARENT_INDEX, o, DONE)) {
                ((DisposableContainer)o).delete(this);
            }

            for (;;) {
                o = get(FUTURE_INDEX);
                if (o == DISPOSED || compareAndSet(FUTURE_INDEX, o, DONE)) {
                    break;
                }
            }
        }
    }

    public void setFuture(Future<?> f) {
        for (;;) {
            Object o = get(FUTURE_INDEX);
            if (o == DONE) {
                return;
            }
            if (o == DISPOSED) {
                f.cancel(true);
                return;
            }
            if (compareAndSet(FUTURE_INDEX, o, f)) {
                return;
            }
        }
    }

    @Override
    public void dispose() {
        for (;;) {
            Object o = get(FUTURE_INDEX);
            if (o == DONE || o == DISPOSED) {
                break;
            }
            if (compareAndSet(FUTURE_INDEX, o, DISPOSED)) {
                if (o != null) {
                    ((Future<?>)o).cancel(true);
                }
                break;
            }
        }

        for (;;) {
            Object o = get(PARENT_INDEX);
            if (o == DONE || o == DISPOSED || o == null) {
                return;
            }
            if (compareAndSet(PARENT_INDEX, o, DISPOSED)) {
                ((DisposableContainer)o).delete(this);
                return;
            }
        }
    }

    @Override
    public boolean isDisposed() {
        Object o = get(FUTURE_INDEX);
        return o == DISPOSED || o == DONE;
    }
}
