package com.google.gson;

public abstract class JsonElement {
    public String getAsString() { return null; }
    public int getAsInt() { return 0; }
    public float getAsFloat() { return 0; }
    public boolean getAsBoolean() { return false; }
    public JsonObject getAsJsonObject() { return null; }
    public JsonArray getAsJsonArray() { return null; }
}
