package com.google.gson;

public class JsonObject extends JsonElement {
    public void addProperty(String name, String value) {}
    public void addProperty(String name, Number value) {}
    public void addProperty(String name, Boolean value) {}
    public void add(String name, JsonElement value) {}
    public JsonElement get(String name) { return null; }
    public boolean has(String name) { return false; }
    public JsonArray getAsJsonArray(String name) { return null; }
}
