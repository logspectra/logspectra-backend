package com.loganalyzer.ai.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SearchReplacePatchApplierTest {

    private final SearchReplacePatchApplier applier = new SearchReplacePatchApplier();

    @Test
    void appliesExactUniqueMatch() {
        String original = """
            class Checkout {
              int total(int n) {
                return 10 / n;
              }
            }
            """;
        String search = "return 10 / n;";
        String replace = "if (n == 0) throw new IllegalArgumentException(\"empty cart\");\n    return 10 / n;";

        String patched = applier.apply(original, search, replace).orElseThrow();
        assertTrue(patched.contains("empty cart"));
        assertTrue(patched.contains("return 10 / n;"));
    }

    @Test
    void rejectsAmbiguousSearch() {
        String original = "foo\nfoo\n";
        assertTrue(applier.apply(original, "foo", "bar").isEmpty());
    }

    @Test
    void stripsLineNumberPrefixesFromSearch() {
        String original = "    return 10 / cartItemCount;\n";
        String search = "  12 |     return 10 / cartItemCount;";
        String patched = applier.apply(original, search, "    return cartItemCount == 0 ? 0 : 10 / cartItemCount;").orElseThrow();
        assertEquals("    return cartItemCount == 0 ? 0 : 10 / cartItemCount;\n", patched);
    }
}
