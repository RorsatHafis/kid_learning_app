package com.platform.curriculum.web;

import java.util.UUID;

/**
 * Curricula as a family or teacher chooses between them: name, subject, age band and the published version an
 * enrolment would be pinned to.
 */
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

    /**
     * A curriculum as a family chooses one for a particular child.
     *
     * @param maxAge      null when the age group has no upper bound
     * @param forChildAge whether it is for the child's age group (the rule automatic enrolment uses)
     * @param ageOrder    the age group's display order, for sorting
     */
    public record CurriculumChoiceResponse(
            UUID id,
            String name,
            String subjectCode,
            String subjectName,
            String ageHubCode,
            String ageHubName,
            int minAge,
            Integer maxAge,
            boolean forChildAge,
            int ageOrder
    ) {
    }

}
