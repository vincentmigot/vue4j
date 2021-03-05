package org.vue4j.utils.bo;

import java.util.function.Consumer;

public class BuildOrderUnique<T> implements BuildOrder<T> {

    private final T item;

    public BuildOrderUnique(T item) {
        this.item = item;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void execute(Consumer<T> action) {
        action.accept(item);
    }

}
