package io.github.skydynamic.quickbackupmulti.translate;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Keeps the two shipped languages in step.
 *
 * <p>A message that exists in one language only falls back to the raw key, and a message whose
 * placeholder count differs throws {@code MissingFormatArgumentException} the moment it is used — both
 * have happened here before (the restore countdown said "10 seconds" in the text itself, so a custom
 * countdown showed the wrong number).
 */
class LangFilesTest {
    @Test
    void everyKeyExistsInBothLanguages() {
        Map<String, String> zh = Translate.getTranslationFromResourcePath("zh_cn");
        Map<String, String> en = Translate.getTranslationFromResourcePath("en_us");

        assertEquals(new TreeSet<>(zh.keySet()), new TreeSet<>(en.keySet()));
    }

    @Test
    void placeholdersMatchBetweenLanguages() {
        Map<String, String> zh = Translate.getTranslationFromResourcePath("zh_cn");
        Map<String, String> en = Translate.getTranslationFromResourcePath("en_us");

        for (String key : new TreeSet<>(zh.keySet())) {
            assertEquals(placeholders(zh.get(key)), placeholders(en.get(key)),
                "placeholder count differs for " + key);
        }
    }

    /** Number of {@code %s}/{@code %d} conversions, ignoring escaped {@code %%}. */
    private static int placeholders(String message) {
        int count = 0;
        for (int i = 0; i < message.length() - 1; i++) {
            if (message.charAt(i) == '%' && message.charAt(i + 1) != '%') {
                count++;
                i++;
            }
        }
        return count;
    }
}
