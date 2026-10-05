package perf;

public interface AccumulationWorkload {
    Object count();
    int checksum(Object result);
}
