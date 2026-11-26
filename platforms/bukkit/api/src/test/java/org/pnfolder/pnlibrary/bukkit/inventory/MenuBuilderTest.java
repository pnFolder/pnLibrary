package org.pnfolder.pnlibrary.bukkit.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MenuBuilderTest {
    @Test
    void chestValidatesSizeAndBuildsBorder() {
        Menu menu = Menus.chest("Test").rows(3).border(new ItemStack(Material.STONE)).build();
        assertEquals(27, menu.getSize());
        assertEquals(20, menu.getItems().size());
        assertThrows(IllegalArgumentException.class, () -> Menus.chest("Bad").rows(7));
    }

    @Test
    void publicCallbacksDoNotExposeKotlinFunctionTypes() {
        List<String> types = new ArrayList<>();
        for (Class<?> type : Arrays.asList(MenuBuilder.class, MenuService.class, MenuSession.class)) {
            for (Method method : type.getDeclaredMethods()) {
                types.addAll(Arrays.asList(method.getParameterTypes()).stream().map(Class::getName)
                    .collect(java.util.stream.Collectors.toList()));
                types.add(method.getReturnType().getName());
            }
        }
        assertFalse(types.stream().anyMatch(name -> name.startsWith("kotlin.jvm.functions.")));
    }
}
