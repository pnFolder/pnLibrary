package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.BackupDatabase;
import org.pnfolder.pnlibrary.api.config.BackupTable;
import org.pnfolder.pnlibrary.api.config.ConfigIgnore;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigOrder;
import org.pnfolder.pnlibrary.api.config.DatabaseField;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.config.DatabaseRole;

class ConfigObjectIntrospectorTest {
    @Test
    void discoversInheritedFieldsAndAppliesFilteringOrderingAndNaming() {
        ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE);

        List<Field> fields = introspector.fields(ChildConfig.class);

        List<String> keys = new ArrayList<String>();
        List<Object> values = new ArrayList<Object>();
        for (Field field : fields) {
            keys.add(introspector.key(field));
            values.add(introspector.read(field, new ChildConfig()));
        }
        assertEquals(Arrays.asList("base-value", "explicit", "child-value"), keys);
        assertEquals(Arrays.<Object>asList(1, 2, 3), values);
    }

    @Test
    void materializesStandardCollectionInterfacesPredictably() {
        ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED);

        Collection<Object> set = introspector.collection(Set.class, Arrays.<Object>asList("a", "a", "b"), "values");
        Map<Object, Object> source = new LinkedHashMap<Object, Object>();
        source.put("a", 1);
        Map<Object, Object> map = introspector.map(Map.class, source, "values");

        assertEquals(new LinkedHashSet<Object>(Arrays.<Object>asList("a", "b")), set);
        assertEquals(source, map);
    }

    private static class BaseConfig {
        @ConfigOrder(-10)
        int baseValue = 1;

        @ConfigIgnore
        int ignored = 0;

        transient int transientValue = 0;
    }

    private static final class ChildConfig extends BaseConfig {
        @ConfigOrder(-5)
        @ConfigKey("explicit")
        int renamed = 2;

        int childValue = 3;

        static final int STATIC_VALUE = 0;
    }

    @BackupDatabase(kind = DatabaseKind.MYSQL, name = "Игроки", tables = {"players"})
    @BackupDatabase(kind = DatabaseKind.MYSQL, name = "Экономика")
    public static class ThreeInOne {
        @DatabaseField(role = DatabaseRole.USER)
        public String user = "";

        @DatabaseField(role = DatabaseRole.DATABASE, database = "Игроки")
        public String playersSchema = "";

        @DatabaseField(role = DatabaseRole.DATABASE, database = "Экономика")
        public String economySchema = "";

        @BackupTable(database = "Экономика")
        public String accounts = "";
    }

    @Test
    void oneClassCanDescribeSeveralDatabasesAndEachFieldJoinsItsOwn() {
        List<DatabasePath> paths = new ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED).databasePaths(ThreeInOne.class);

        assertEquals(Arrays.asList("Игроки", "Экономика"), names(paths));
        assertEquals(keys(DatabaseRole.USER, "user", DatabaseRole.DATABASE, "playersSchema"), paths.get(0).getKeys());
        assertEquals(keys(DatabaseRole.USER, "user", DatabaseRole.DATABASE, "economySchema"), paths.get(1).getKeys());
        assertEquals(Collections.<String>emptyList(), paths.get(0).getTableKeys());
        assertEquals(Collections.singletonList("accounts"), paths.get(1).getTableKeys());
    }

    public static class OnTheFields {
        @BackupDatabase(kind = DatabaseKind.MYSQL, name = "Игроки")
        public String playersDb = "";

        @DatabaseField(role = DatabaseRole.HOST, database = "Игроки")
        public String playersHost = "";

        @BackupDatabase(kind = DatabaseKind.POSTGRESQL, name = "Кланы", tables = {"clans"})
        public String clansDb = "";

        @DatabaseField(role = DatabaseRole.HOST, database = "Кланы")
        public String clansHost = "";
    }

    @Test
    void aDatabaseCanBeDescribedOnItsFieldsAloneWithNoAnnotationOnTheClass() {
        List<DatabasePath> paths = new ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED).databasePaths(OnTheFields.class);

        assertEquals(Arrays.asList("Игроки", "Кланы"), names(paths));
        List<DatabaseKind> kinds = new ArrayList<DatabaseKind>();
        for (DatabasePath path : paths) {
            kinds.add(path.getKind());
        }
        assertEquals(Arrays.asList(DatabaseKind.MYSQL, DatabaseKind.POSTGRESQL), kinds);
        assertEquals(keys(DatabaseRole.HOST, "playersHost", DatabaseRole.DATABASE, "playersDb"), paths.get(0).getKeys());
        assertEquals(keys(DatabaseRole.HOST, "clansHost", DatabaseRole.DATABASE, "clansDb"), paths.get(1).getKeys());
        assertEquals("", paths.get(0).getPath());
    }

    private static List<String> names(List<DatabasePath> paths) {
        List<String> names = new ArrayList<String>();
        for (DatabasePath path : paths) {
            names.add(path.getName());
        }
        return names;
    }

    private static Map<DatabaseRole, String> keys(DatabaseRole first, String firstKey, DatabaseRole second, String secondKey) {
        Map<DatabaseRole, String> keys = new LinkedHashMap<DatabaseRole, String>();
        keys.put(first, firstKey);
        keys.put(second, secondKey);
        return keys;
    }
}
