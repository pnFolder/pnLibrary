package org.pnfolder.pnlibrary.config.yaml;

import org.pnfolder.pnlibrary.config.yaml.schema.ConfigObjectIntrospector;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigSecret;

class ConfigSecretTest {
    public static class Database {
        public String host = "localhost";

        @ConfigSecret
        public String password = "";
    }

    public static class Settings {
        public Database database = new Database();

        @ConfigSecret
        public String webhookUrl = "";

        public String welcome = "Hi";
    }

    public static class Account {
        public String name = "";

        @ConfigSecret
        public String pin = "";
    }

    public static class Holders {
        public List<Account> users = Collections.emptyList();
        public Map<String, Account> byName = Collections.emptyMap();
    }

    @Test
    void secretsInsideListItemsAndMapValuesAreFound() {
        List<String> paths = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE).secretPaths(Holders.class);

        assertEquals(Arrays.asList("users.pin", "by-name.*.pin"), paths);
    }

    @Test
    void secretFieldsAreFoundAtAnyDepthUnderTheirFileKeys() {
        List<String> paths = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE).secretPaths(Settings.class);

        assertEquals(Arrays.asList("database.password", "webhook-url"), paths);
    }
}
