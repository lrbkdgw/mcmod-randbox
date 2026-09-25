package com.mojang.brigadier.builder;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(ArgumentBuilder<S, ?> argument) { return null; }
    public T executes(Command<S> command) { return null; }
    public T requires(Predicate<S> requirement) { return null; }
}
