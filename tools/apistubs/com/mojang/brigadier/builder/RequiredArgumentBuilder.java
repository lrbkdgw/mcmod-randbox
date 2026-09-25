package com.mojang.brigadier.builder;

import com.mojang.brigadier.suggestion.SuggestionProvider;

public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
    public RequiredArgumentBuilder<S, T> suggests(SuggestionProvider<S> provider) { return this; }
}
