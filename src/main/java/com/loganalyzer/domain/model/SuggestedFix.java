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
    /** Full replacement file contents when the model proposes a source change. */
    private String newContent;
}
