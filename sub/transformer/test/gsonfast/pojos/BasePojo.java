package gsonfast.pojos;

public class BasePojo {
    protected long base;
    private String basePrivate;

    public BasePojo() {
    }

    public void fill(long b, String bp) {
        this.base = b;
        this.basePrivate = bp;
    }

    public String basePrivate() {
        return basePrivate;
    }
}
