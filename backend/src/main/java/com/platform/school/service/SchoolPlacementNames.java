package com.platform.school.service;

import com.platform.learning.service.PlacementNames;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** "Grade 1 A · Wat Phnom Primary": the class and school a family joined, as it knows them. */
@Component
public class SchoolPlacementNames implements PlacementNames {

    private final SchoolClassRepository classes;
    private final SchoolRepository schools;

    public SchoolPlacementNames(SchoolClassRepository classes, SchoolRepository schools) {
        this.classes = classes;
        this.schools = schools;
    }

    @Override
    public Optional<String> classLabel(UUID classId) {
        return classes.findById(classId).map(schoolClass -> schools.findById(schoolClass.getSchoolId())
                .map(school -> schoolClass.getName() + " · " + school.getName())
                .orElse(schoolClass.getName()));
    }
}
