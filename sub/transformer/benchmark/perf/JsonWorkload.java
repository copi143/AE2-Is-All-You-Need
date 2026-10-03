package perf;

/** A shared interface keeps reflection and class-loader lookup outside timed operations. */
public interface JsonWorkload {
    String write();
    Object read();
    default Object bind() { throw new UnsupportedOperationException(); }
    default Object newGson() { throw new UnsupportedOperationException(); }
}
