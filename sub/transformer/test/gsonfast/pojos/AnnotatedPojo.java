package gsonfast.pojos;

import com.google.gson.annotations.SerializedName;

public class AnnotatedPojo {
    @SerializedName(value = "x", alternate = {"y", "z"})
    public int value;

    @SerializedName("renamed")
    public String other;

    public transient int skipped = 7;

    public AnnotatedPojo() {
    }
}
