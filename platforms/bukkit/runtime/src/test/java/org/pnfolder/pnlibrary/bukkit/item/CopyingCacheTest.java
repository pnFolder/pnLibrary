package org.pnfolder.pnlibrary.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CopyingCacheTest {
    private static final class Stack {
        private String name;

        Stack(String name) {
            this.name = name;
        }
    }

    private static CopyingCache<Stack> cache(int capacity) {
        return new CopyingCache<>(capacity, stack -> new Stack(stack.name));
    }

    @Test
    void callersGetCopiesAndCannotChangeWhatIsStored() {
        CopyingCache<Stack> cache = cache(4);
        Stack original = new Stack("head");
        cache.set("a", original);
        original.name = "changed after storing";

        Stack first = cache.get("a");
        first.name = "changed after reading";

        assertNotSame(first, cache.get("a"));
        assertEquals("head", cache.get("a").name);
    }

    @Test
    void theCacheNeverGrowsPastItsCapacityAndDropsTheLeastRecentlyUsed() {
        CopyingCache<Stack> cache = cache(3);
        for (String key : Arrays.asList("a", "b", "c")) {
            cache.set(key, new Stack(key));
        }
        cache.get("a");
        cache.set("d", new Stack("d"));

        assertEquals(3, cache.size());
        assertNull(cache.get("b"));
        Set<String> present = new HashSet<>();
        for (String key : Arrays.asList("a", "c", "d")) {
            if (cache.get(key) != null) {
                present.add(key);
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("a", "c", "d")), present);
        for (int index = 0; index < 1000; index++) {
            cache.set("k" + index, new Stack("k" + index));
        }
        assertEquals(3, cache.size());
    }

    @Test
    void parallelUseStaysWithinCapacity() throws InterruptedException {
        CopyingCache<Stack> cache = cache(16);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int thread = 0; thread < 4; thread++) {
            int id = thread;
            pool.execute(() -> {
                for (int index = 0; index < 2000; index++) {
                    cache.set(id + "-" + index % 50, new Stack("x"));
                    cache.get(id + "-" + index % 50);
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(cache.size() <= 16);
    }
}
