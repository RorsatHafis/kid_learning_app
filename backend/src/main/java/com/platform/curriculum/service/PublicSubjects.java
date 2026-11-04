package com.platform.curriculum.service;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.LocalizableContentType;
import com.platform.content.service.CatalogNames;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SUB-1 / FLEX-1: the subjects the public pages show, from the catalog - as many as there are, never a fixed three. A
 * subject with a published family curriculum (platform content, the catalog families choose from) is available, with
 * the ages its curricula cover; an active subject without one is in preparation. Names and descriptions in the
 * reader's language where the catalog has Khmer. Teachers' own curricula are not on the public pages.
 */
@Service
public class PublicSubjects {

    /** {@code maxAge} null: no upper age. Ages are null for a subject in preparation. */
    public record PublicSubject(String code, String name, String description, boolean available, Integer minAge, Integer maxAge) {
    }

    private final SubjectRepository subjects;
    private final AgeHubRepository ageHubs;
    private final CurriculumService curricula;
    private final CatalogNames names;

    public PublicSubjects(SubjectRepository subjects, AgeHubRepository ageHubs, CurriculumService curricula, CatalogNames names) {
        this.subjects = subjects;
        this.ageHubs = ageHubs;
        this.curricula = curricula;
        this.names = names;
    }

    @Transactional(readOnly = true)
    public List<PublicSubject> offered() {
        List<Subject> active = subjects.findByStatus(CatalogStatus.ACTIVE);
        if (active.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Curriculum>> published = curricula.listFamilyCatalog().stream().collect(Collectors.groupingBy(Curriculum::getSubjectId));
        Map<UUID, AgeHub> hubs = ageHubs.findAllById(published.values().stream().flatMap(List::stream).map(Curriculum::getAgeHubId).distinct().toList())
                .stream().collect(Collectors.toMap(AgeHub::getId, Function.identity()));
        names.prefetch(LocalizableContentType.SUBJECT, active.stream().map(Subject::getId).toList());
        return active.stream()
                .map(subject -> {
                    List<AgeHub> covered = published.getOrDefault(subject.getId(), List.of()).stream()
                            .map(c -> hubs.get(c.getAgeHubId())).filter(java.util.Objects::nonNull).toList();
                    boolean available = !published.getOrDefault(subject.getId(), List.of()).isEmpty();
                    Integer min = covered.stream().map(AgeHub::getMinAge).min(Integer::compare).orElse(null);
                    Integer max = covered.isEmpty() || covered.stream().anyMatch(h -> h.getMaxAge() == null) ? null
                            : covered.stream().map(AgeHub::getMaxAge).max(Integer::compare).orElse(null);
                    return new PublicSubject(subject.getCode(),
                            names.name(LocalizableContentType.SUBJECT, subject.getId(), subject.getName()),
                            names.description(LocalizableContentType.SUBJECT, subject.getId(), subject.getDescription()),
                            available, available ? min : null, available ? max : null);
                })
                // Available first, then by name.
                .sorted(Comparator.comparing((PublicSubject s) -> !s.available()).thenComparing(PublicSubject::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
