package com.platform.content.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.LessonSkill;
import com.platform.content.entity.LessonVersion;
import com.platform.content.repository.LessonRepository;
import com.platform.content.repository.LessonSkillRepository;
import com.platform.content.repository.LessonVersionRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonVersionRepository lessonVersionRepository;
    private final LessonSkillRepository lessonSkillRepository;
    private final SkillRepository skillRepository;

    public LessonService(LessonRepository lessonRepository, LessonVersionRepository lessonVersionRepository,
                          LessonSkillRepository lessonSkillRepository, SkillRepository skillRepository) {
        this.lessonRepository = lessonRepository;
        this.lessonVersionRepository = lessonVersionRepository;
        this.lessonSkillRepository = lessonSkillRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public Lesson createLesson(UUID subjectId, String title) {
        return lessonRepository.save(Lesson.create(subjectId, title));
    }

    @Transactional
    public Lesson createOwnedLesson(UUID subjectId, String title, UUID ownerAccountId) {
        return lessonRepository.save(Lesson.create(subjectId, title, ownerAccountId));
    }

    @Transactional(readOnly = true)
    public Lesson getLesson(UUID id) {
        return lessonRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lesson", id));
    }

    @Transactional
    public LessonVersion createDraftVersion(UUID lessonId, String body) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        int nextVersionNumber = lessonVersionRepository.findTopByLessonIdOrderByVersionNumberDesc(lessonId)
                .map(previous -> previous.getVersionNumber() + 1)
                .orElse(1);
        return lessonVersionRepository.save(LessonVersion.draft(lessonId, nextVersionNumber, body));
    }

    @Transactional(readOnly = true)
    public LessonVersion getVersion(UUID lessonVersionId) {
        return lessonVersionRepository.findById(lessonVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("LessonVersion", lessonVersionId));
    }

    @Transactional(readOnly = true)
    public LessonVersion getPublishedVersion(UUID lessonId) {
        return lessonVersionRepository.findFirstByLessonIdAndStatusOrderByVersionNumberDesc(lessonId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published LessonVersion for lesson", lessonId));
    }

    @Transactional
    public void updateDraftBody(UUID lessonVersionId, String newBody) {
        LessonVersion version = getVersion(lessonVersionId);
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Body can only be edited on a DRAFT version, %s is %s".formatted(lessonVersionId, version.getStatus()));
        }
        version.updateBody(newBody);
    }

    /** See CurriculumService.publish() for the retire-before-publish ordering rationale (V20 partial unique index). */
    @Transactional
    public LessonVersion publish(UUID lessonVersionId) {
        LessonVersion version = getVersion(lessonVersionId);

        lessonVersionRepository
                .findFirstByLessonIdAndStatusOrderByVersionNumberDesc(version.getLessonId(), PublicationStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.retire();
                    lessonVersionRepository.saveAndFlush(previouslyPublished);
                });

        version.publish(Instant.now());
        return lessonVersionRepository.saveAndFlush(version);
    }

    @Transactional
    public LessonSkill tagSkill(UUID lessonId, UUID skillId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return lessonSkillRepository.save(LessonSkill.create(lessonId, skillId));
    }

    @Transactional(readOnly = true)
    public List<LessonSkill> listSkills(UUID lessonId) {
        return lessonSkillRepository.findByLessonId(lessonId);
    }

}
