package com.mojang.brigadier.suggestion;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface SuggestionProvider<S> {
    CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
            throws CommandSyntaxException;
}
