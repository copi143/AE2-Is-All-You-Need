package gsonfast.pojos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonConsumer {
    public static Gson makeGson() {
        return new Gson();
    }

    public static Gson makeGsonFromBuilder() {
        return new GsonBuilder().create();
    }

    public static Gson makePrettyGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
