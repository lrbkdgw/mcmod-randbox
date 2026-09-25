package net.minecraft.commands;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.resources.ResourceLocation;

public interface SharedSuggestionProvider {
    static CompletableFuture<Suggestions> suggestResource(Iterable<ResourceLocation> ids, SuggestionsBuilder builder) {
        return null;
    }
}
