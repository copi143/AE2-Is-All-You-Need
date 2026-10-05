package it.unimi.dsi.fastutil.objects;

/** No class-loader retention and no speculative execution of key methods. */
final class IdentityKeyClasses extends ClassValue<Boolean> {
    @Override
    protected Boolean computeValue(Class<?> type) {
        try {
            return type.getMethod("hashCode").getDeclaringClass() == Object.class
                    && type.getMethod("equals", Object.class).getDeclaringClass() == Object.class;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            return false;
        }
    }
}
