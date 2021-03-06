/**
 * Copyright 2012 Alex Yanchenko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.droidparts.inject.injector;

import static android.content.pm.PackageManager.GET_META_DATA;
import static org.droidparts.reflect.util.ReflectionUtils.setFieldVal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.droidparts.contract.Constants.ManifestMeta;
import org.droidparts.inject.AbstractDependencyProvider;
import org.droidparts.util.L;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class DependencyInjector {

	private static volatile boolean inited = false;
	private static AbstractDependencyProvider dependencyProvider;
	private static HashMap<Class<?>, Method> methodRegistry = new HashMap<Class<?>, Method>();

	static void init(Context ctx) {
		if (!inited) {
			synchronized (DependencyInjector.class) {
				if (!inited) {
					dependencyProvider = getDependencyProvider(ctx);
					if (dependencyProvider != null) {
						Method[] methods = dependencyProvider.getClass()
								.getMethods();
						for (Method method : methods) {
							methodRegistry.put(method.getReturnType(), method);
						}
					}
					inited = true;
				}
			}
		}
	}

	static void tearDown() {
		if (dependencyProvider != null) {
			dependencyProvider.getDB().close();
		}
		dependencyProvider = null;
	}

	static boolean inject(Context ctx, Object target, Field field) {
		init(ctx);
		Object val = getDependency(ctx, field.getType());
		if (val != null) {
			try {
				setFieldVal(target, field, val);
				return true;
			} catch (IllegalArgumentException e) {
				// swallow
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getDependency(Context ctx, Class<T> cls) {
		init(ctx);
		if (dependencyProvider != null) {
			Method method = methodRegistry.get(cls);
			if (method != null) {
				T val = null;
				try {
					int paramCount = method.getGenericParameterTypes().length;
					if (paramCount == 0) {
						val = (T) method.invoke(dependencyProvider);
					} else {
						val = (T) method.invoke(dependencyProvider, ctx);
					}
					return val;
				} catch (Exception e) {
					L.e("No valid dependency method for " + cls.getName());
					L.d(e);
				}
			}
		}
		return null;
	}

	private static AbstractDependencyProvider getDependencyProvider(Context ctx) {
		PackageManager pm = ctx.getPackageManager();
		String className = null;
		try {
			Bundle metaData = pm.getApplicationInfo(ctx.getPackageName(),
					GET_META_DATA).metaData;
			className = metaData.getString(ManifestMeta.DEPENDENCY_PROVIDER);
		} catch (Exception e) {
			L.d(e);
		}
		if (className == null) {
			L.e("No <meta-data android:name=\"droidparts_dependency_provider\" android:value=\"...\"/> in AndroidManifest.xml.");
			return null;
		}
		if (className.startsWith(".")) {
			className = ctx.getPackageName() + className;
		}
		try {
			Class<?> cls = Class.forName(className);
			Constructor<?> constr = cls.getConstructor(Context.class);
			AbstractDependencyProvider adp = (AbstractDependencyProvider) constr
					.newInstance(ctx.getApplicationContext());
			return adp;
		} catch (Exception e) {
			L.e("Not a valid DroidParts dependency provider: " + className);
			L.d(e);
			return null;
		}
	}

}
