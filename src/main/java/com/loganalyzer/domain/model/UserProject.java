package com.loganalyzer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProject {

    @EmbeddedId
    private UserProjectId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserProjectRole role;
}
