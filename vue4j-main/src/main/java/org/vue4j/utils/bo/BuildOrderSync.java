package org.vue4j.utils.bo;

import java.util.LinkedList;
import java.util.function.Consumer;

public class BuildOrderSync<T> implements BuildOrder<T> {

    protected final LinkedList<BuildOrder<T>> items = new LinkedList<>();

    public void AddItem(T item) {
        items.add(new BuildOrderUnique(item));
    }

    public void addNext(BuildOrder<T> bo) {
        items.add(bo);
    }

    @Override
    public void execute(Consumer<T> action) {
        items.stream().forEach(bo -> {
            bo.execute(action);
        });
    }

    @Override
    public void executeReverse(Consumer<T> action) {
        items.descendingIterator().forEachRemaining(bo -> {
            bo.execute(action);
        });
    }

    @Override
    public boolean isEmpty() {
        return items.size() > 0;
    }
}
