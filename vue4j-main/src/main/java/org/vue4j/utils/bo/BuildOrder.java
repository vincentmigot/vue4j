package org.vue4j.utils.bo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public interface BuildOrder<T> {

    public void execute(Consumer<T> action);

    public default void executeReverse(Consumer<T> action) {
        execute(action);
    }
    
    public default void executeSync(Consumer<T> action) {
        execute(action);
    }
    
    public default void executeReverseSync(Consumer<T> action) {
        execute(action);
    }
    public boolean isEmpty();

    public static <T> BuildOrder<T> getBuildOrderByDependencies(Set<T> items, Function<T, String> idGetter, Function<T, Set<T>> dependenciesGetter) throws BuildOrderUnsolvableException {
        BuildOrderSync<T> dependencyBuildOrder = new BuildOrderSync<T>();
        BuildOrderAsync<T> rootDependencyBuildOrder = new BuildOrderAsync<T>();

        int remainingItemCount = items.size();
        Set<T> remainingItems = new HashSet<>(remainingItemCount);

        Set<String> registredItemsIDs = new HashSet<>();

        Set<String> resolvedItems = new HashSet<>();
        Map<String, Set<String>> itemDependencies = new HashMap<>();
        for (T item : items) {
            String itemID = idGetter.apply(item);
            registredItemsIDs.add(itemID);

            if (!itemDependencies.containsKey(itemID)) {
                Set<T> dependencies = dependenciesGetter.apply(item);
                Set<String> dependenciesIDs = new HashSet<>();
                dependencies.forEach((dependency) -> {
                    String dependencyID = idGetter.apply(dependency);
                    dependenciesIDs.add(dependencyID);
                    if (!registredItemsIDs.contains(dependencyID)) {
                        registredItemsIDs.add(dependencyID);
                        remainingItems.add(dependency);
                    }

                });
                itemDependencies.put(itemID, dependenciesIDs);
            }
            Set<String> dependencies = itemDependencies.get(itemID);

            if (dependencies.size() == 0) {
                rootDependencyBuildOrder.addNext(new BuildOrderUnique<T>(item));
                resolvedItems.add(itemID);
            } else {
                remainingItems.add(item);
            }
        }

        if (remainingItems.size() > 0 && remainingItemCount == items.size()) {
            throw new BuildOrderUnsolvableException();
        }

        dependencyBuildOrder.addNext(rootDependencyBuildOrder);

        while (remainingItems.size() > 0) {
            BuildOrderAsync<T> loopDependencyBuildOrder = new BuildOrderAsync<T>();
            final AtomicBoolean loopEvolution = new AtomicBoolean(false);

            for (T item : remainingItems) {

                String itemID = idGetter.apply(item);

                if (!itemDependencies.containsKey(itemID)) {
                    Set<T> dependencies = dependenciesGetter.apply(item);
                    Set<String> dependenciesIDs = new HashSet<>();
                    dependencies.forEach((dependency) -> {
                        String dependencyID = idGetter.apply(dependency);
                        dependenciesIDs.add(dependencyID);
                        if (!registredItemsIDs.contains(dependencyID)) {
                            registredItemsIDs.add(dependencyID);
                            remainingItems.add(dependency);
                            loopEvolution.set(true);
                        }

                    });
                    itemDependencies.put(itemID, dependenciesIDs);
                }
                Set<String> dependencies = itemDependencies.get(itemID);

                if (resolvedItems.contains(itemID)) {
                    remainingItems.remove(item);
                    loopEvolution.set(true);
                } else {

                    for (String dependencyID : dependencies) {
                        if (resolvedItems.contains(dependencyID)) {
                            dependencies.remove(dependencyID);
                        }
                    }

                    if (dependencies.size() == 0) {
                        remainingItems.remove(item);
                        loopEvolution.set(true);
                        loopDependencyBuildOrder.addNext(new BuildOrderUnique<T>(item));
                    }

                }
            }

            if (!loopEvolution.get()) {
                throw new BuildOrderUnsolvableException();
            }

            if (!loopDependencyBuildOrder.isEmpty()) {
                dependencyBuildOrder.addNext(loopDependencyBuildOrder);
            }
        }

        return dependencyBuildOrder;
    }
}
