package org.pnfolder.pnlibrary.api.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemotePolicyResultTest {
    @Test
    void plainDecisionsCarryNoCustomCard() {
        RemotePolicyResult denied = RemotePolicyResult.deny("Версия устарела");

        assertFalse(denied.getAllowed());
        assertEquals("Версия устарела", denied.getMessage());
        assertNull(denied.getStatus());
        assertNull(denied.getHeadline());
        assertTrue(denied.getDetails().isEmpty() && denied.getSteps().isEmpty() && denied.getLinks().isEmpty());
        assertTrue(RemotePolicyResult.allow().getAllowed());
    }

    @Test
    void aBuiltDecisionKeepsItsCardInTheOrderItWasWritten() {
        RemotePolicyResult result = RemotePolicyResult.denying("Версия устарела")
            .status("устарела")
            .headline("нужно обновление")
            .reason("Минимум 0.0.2")
            .reason("Группа", it -> it.child("вложенная причина"))
            .detail("Установлена", "0.0.1")
            .detail("Минимальная", "0.0.2")
            .step("Скачайте новую версию")
            .step("Перезапустите сервер")
            .link("Скачать", "https://example.org/download")
            .build();

        assertFalse(result.getAllowed());
        assertEquals("устарела", result.getStatus());
        assertEquals("нужно обновление", result.getHeadline());
        assertEquals(Arrays.asList("Установлена", "Минимальная"), new ArrayList<String>(result.getDetails().keySet()));
        assertEquals(Arrays.asList("Скачайте новую версию", "Перезапустите сервер"), result.getSteps());
        assertEquals("https://example.org/download", result.getLinks().get("Скачать"));
        List<String> reasons = new ArrayList<String>();
        for (RemotePolicyExplanation child : result.getExplanation().getChildren()) {
            reasons.add(child.getText());
        }
        assertEquals(Arrays.asList("Минимум 0.0.2", "Группа"), reasons);
    }

    @Test
    void blankValuesAreRejectedOrDropped() {
        RemotePolicyResult.Builder builder = RemotePolicyResult.allowing("ok").status("   ").headline("");
        assertNull(builder.build().getStatus());
        assertNull(builder.build().getHeadline());
        assertThrows(IllegalArgumentException.class, () -> RemotePolicyResult.denying("x").step(" "));
        assertThrows(IllegalArgumentException.class, () -> RemotePolicyResult.denying("x").link("", "https://a.b"));
        assertThrows(IllegalArgumentException.class, () -> RemotePolicyResult.denying("x").detail(" ", "v"));
    }
}
