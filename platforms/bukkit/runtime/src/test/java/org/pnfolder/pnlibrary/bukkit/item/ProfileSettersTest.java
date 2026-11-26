package org.pnfolder.pnlibrary.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;

class ProfileSettersTest {
    public interface FakeTextures {
        void setSkin(URL url);
    }

    public interface FakeProfile {
        FakeTextures getTextures();

        void setTextures(FakeTextures textures);
    }

    public interface FakeSkull {
        void setOwnerProfile(FakeProfile profile);
    }

    public static final class FakeBukkit {
        static final List<String> LOG = new ArrayList<>();

        private FakeBukkit() {
        }

        public static FakeProfile createPlayerProfile(UUID id, String name) {
            LOG.add("create " + id + " " + name);
            FakeTextures textures = url -> LOG.add("skin " + url);
            return new FakeProfile() {
                @Override
                public FakeTextures getTextures() {
                    LOG.add("getTextures");
                    return textures;
                }

                @Override
                public void setTextures(FakeTextures ignored) {
                    LOG.add("setTextures");
                }
            };
        }
    }

    private static final class CountingReflection implements Reflection {
        private int lookups;
        private final Map<String, Class<?>> types = new HashMap<>();

        CountingReflection() {
            types.put("org.bukkit.profile.PlayerProfile", FakeProfile.class);
            types.put("org.bukkit.profile.PlayerTextures", FakeTextures.class);
        }

        @Override
        public Class<?> type(String name) throws ClassNotFoundException {
            Class<?> type = types.get(name);
            if (type == null) {
                throw new ClassNotFoundException(name);
            }
            return type;
        }

        @Override
        public Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
            lookups++;
            return type.getMethod(name, parameters);
        }
    }

    private final List<URL> urls = urls();

    private static List<URL> urls() {
        List<URL> urls = new ArrayList<>();
        try {
            for (int index = 1; index <= 100; index++) {
                urls.add(new URI("https://textures.minecraft.net/texture/hash" + index).toURL());
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return urls;
    }

    private SkullMeta skull() {
        return (SkullMeta) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {SkullMeta.class, FakeSkull.class}, (proxy, method, args) -> {
                if (method.getName().equals("setOwnerProfile")) {
                    FakeBukkit.LOG.add("owner");
                }
                return null;
            });
    }

    /** The way heads were made before: every class and method looked up again for each head. */
    private void oldWay(CountingReflection reflection, URL url, SkullMeta meta) throws Exception {
        UUID id = UUID.nameUUIDFromBytes(("pnLibrary:" + url).getBytes(StandardCharsets.UTF_8));
        String name = "pn" + id.toString().replace("-", "").substring(0, 14);
        Class<?> profileType = reflection.type("org.bukkit.profile.PlayerProfile");
        Class<?> texturesType = reflection.type("org.bukkit.profile.PlayerTextures");
        Object profile = reflection.method(FakeBukkit.class, "createPlayerProfile", UUID.class, String.class)
            .invoke(null, id, name);
        Object textures = reflection.method(profileType, "getTextures").invoke(profile);
        reflection.method(texturesType, "setSkin", URL.class).invoke(textures, url);
        reflection.method(profileType, "setTextures", texturesType).invoke(profile, textures);
        reflection.method(FakeSkull.class, "setOwnerProfile", profileType).invoke(meta, profile);
    }

    @Test
    void theSetterMakesTheSameCallsAsTheLookupOnEveryHeadDid() throws Exception {
        CountingReflection reflection = new CountingReflection();
        ModernProfileSetter setter = ModernProfileSetter.resolve(reflection, FakeBukkit.class, FakeSkull.class);
        assertNotNull(setter);

        FakeBukkit.LOG.clear();
        for (URL url : urls) {
            oldWay(reflection, url, skull());
        }
        List<String> before = new ArrayList<>(FakeBukkit.LOG);
        FakeBukkit.LOG.clear();
        for (URL url : urls) {
            setter.apply(skull(), url);
        }

        assertEquals(before, new ArrayList<>(FakeBukkit.LOG));
    }

    @Test
    void aHundredHeadsLookUpEachMethodOnceInsteadOfOncePerHead() throws Exception {
        CountingReflection old = new CountingReflection();
        for (URL url : urls) {
            oldWay(old, url, skull());
        }

        CountingReflection fresh = new CountingReflection();
        ModernProfileSetter setter = ModernProfileSetter.resolve(fresh, FakeBukkit.class, FakeSkull.class);
        for (URL url : urls) {
            setter.apply(skull(), url);
        }

        assertEquals(500, old.lookups);
        assertEquals(5, fresh.lookups);
    }

    @Test
    void aServerWithoutTheProfileApiGivesNoSetterAfterASingleFailedLookup() {
        int[] types = {0};
        Reflection reflection = new Reflection() {
            @Override
            public Class<?> type(String name) throws ClassNotFoundException {
                types[0]++;
                throw new ClassNotFoundException(name);
            }

            @Override
            public Method method(Class<?> type, String name, Class<?>... parameters) {
                throw new IllegalStateException("unreachable");
            }
        };
        assertNull(ModernProfileSetter.resolve(reflection));
        assertNull(LegacyProfileSetter.resolve(reflection));
        assertEquals(2, types[0]);
    }
}
