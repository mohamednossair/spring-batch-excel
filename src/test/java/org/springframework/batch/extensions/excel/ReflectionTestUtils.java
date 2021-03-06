package org.springframework.batch.extensions.excel;

import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * Simplified version of {@code ReflectionTestUtils} from Spring. This
 * to prevent a unneeded dependency on the Spring Test module.
 *
 * @author Marten Deinum
 * @since 2.0.0
 */
public class ReflectionTestUtils {

	@Nullable
	public static Object getField(Object targetObject, String name) {
		Class<?> targetClass = targetObject.getClass();

		Field field = ReflectionUtils.findField(targetClass, name);
		if (field == null) {
			throw new IllegalArgumentException(String.format("Could not find field '%s' on %s or target class [%s]",
					name, targetObject, targetClass));
		}

		ReflectionUtils.makeAccessible(field);
		return ReflectionUtils.getField(field, targetObject);
	}


}
