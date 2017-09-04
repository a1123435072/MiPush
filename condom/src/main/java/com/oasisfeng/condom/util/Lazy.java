/*
 * Copyright (C) 2017 Oasis Feng. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.condom.util;

import android.support.annotation.Keep;
import android.support.annotation.RestrictTo;

/**
 * Helper class for lazy initialization.
 *
 * Created by Oasis on 2017/4/21.
 */
@Keep @RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class Lazy<T> {

	protected abstract T create();

	public final T get() {
		synchronized (this) {
			return mInstance != null ? mInstance : (mInstance = create());
		}
	}

	private T mInstance;
}
