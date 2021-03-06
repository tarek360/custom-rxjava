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

import custom.reactivex.*;
import custom.reactivex.exceptions.Exceptions;
import custom.reactivex.plugins.RxJavaPlugins;

public final class CompletableLift extends Completable {

    final CompletableSource source;

    final CompletableOperator onLift;

    public CompletableLift(CompletableSource source, CompletableOperator onLift) {
        this.source = source;
        this.onLift = onLift;
    }

    @Override
    protected void subscribeActual(CompletableObserver s) {
        try {
            // TODO plugin wrapping

            CompletableObserver sw = onLift.apply(s);

            source.subscribe(sw);
        } catch (NullPointerException ex) { // NOPMD
            throw ex;
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            RxJavaPlugins.onError(ex);
        }
    }

}
