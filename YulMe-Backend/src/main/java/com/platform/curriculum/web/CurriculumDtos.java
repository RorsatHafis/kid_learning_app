package com.platform.curriculum.web;

import java.util.UUID;

public final class CurriculumDtos {

    private CurriculumDtos() {
    }

    public record CurriculumSummaryResponse(
            UUID id,
            String name,
            String subjectCode,
            String subjectName,
            String ageHubCode,
            String ageHubName
    ) {
    }

}
