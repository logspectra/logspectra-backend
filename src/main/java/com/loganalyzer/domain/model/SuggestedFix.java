package com.loganalyzer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedFix {
    private String fix;
    private String priority;
    private String effort;
    /** Repo-relative path to patch, e.g. src/main/java/.../Foo.java */
    private String filePath;
    /**
     * Exact existing source block to find (preferred over {@link #newContent}).
     * Pair with {@link #replace}.
     */
    private String search;
    /** Replacement for {@link #search}. */
    private String replace;
    /** Full replacement file contents when a search/replace patch is not possible. */
    private String newContent;
}
