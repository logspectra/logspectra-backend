package com.loganalyzer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProjectId implements Serializable {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID projectId;
}
