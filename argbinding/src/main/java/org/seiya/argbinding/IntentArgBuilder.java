/*
 * Copyright (C) 2019 ZhengAn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seiya.argbinding;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * The base class of intent arg builder.
 *
 * @author ZhengAn
 * @date 2019/2/12
 */
abstract class IntentArgBuilder<T extends IntentArgBuilder<T>> extends ArgBuilder<T> {
    Context context;
    private int intentFlags;

    /**
     * Get Target class.
     *
     * @return
     */
    protected abstract Class<? extends Context> getTargetClass();

    /**
     * Build the intent.
     */
    @Override
    public Intent build() {
        Intent intent = new Intent();
        intent.putExtras(args);
        intent.setFlags(intentFlags);
        if (context != null) {
            intent.setClass(context, getTargetClass());
        }
        return intent;
    }

    /**
     * Set context for Intent.setClass().
     * @param context
     * @return
     */
    public T setContext(@NonNull Context context) {
        this.context = context;
        return self();
    }

    /**
     * Set flags for Intent.setFlags().
     * @param intentFlags
     * @return
     */
    public T setIntentFlags(int intentFlags) {
        this.intentFlags = intentFlags;
        return self();
    }

    protected void checkContextNull() {
        if (context == null) {
            throw new IllegalArgumentException("Context is null, please set context.");
        }
    }

    public abstract void start();
}
