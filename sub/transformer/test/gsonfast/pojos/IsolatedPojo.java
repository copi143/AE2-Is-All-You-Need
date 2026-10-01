package gsonfast.pojos;

import com.google.gson.annotations.SerializedName;

public class IsolatedPojo {
    public int a;

    @SerializedName(value = "b", alternate = {"bAlt"})
    public String b;

    public IsolatedNested nested;

    public IsolatedPojo() {
    }
}
