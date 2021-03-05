package org.vue4j.utils.bo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BuildOrderAsync<T> extends BuildOrderSync<T> {

    @Override
    public void execute(Consumer<T> action) {
        items.parallelStream().forEach(bo -> {
            bo.execute(action);
        });
    }
}
