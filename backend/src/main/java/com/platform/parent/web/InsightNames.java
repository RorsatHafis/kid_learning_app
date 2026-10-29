package com.platform.parent.web;

import com.platform.content.entity.LocalizableContentType;
import com.platform.content.service.CatalogNames;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.identity.UiLanguage;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.parent.entity.ParentInsight;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * The skill or chapter an insight sentence names, in Khmer for a Khmer reader (LOCALIZATION.md "Content in two
 * languages", slice 3). An insight records its sentence in English with the name as it was then; {@link InsightText}
 * translates the sentence, and this swaps the name the sentence carries for its Khmer, found through the insight's
 * {@code skillId} / {@code learningObjectiveId}. A name renamed since the sentence was recorded is left as recorded.
 */
@Component
public class InsightNames {

    private final SkillRepository skills;
    private final LearningObjectiveRepository objectives;
    private final CatalogNames catalogNames;

    public InsightNames(SkillRepository skills, LearningObjectiveRepository objectives, CatalogNames catalogNames) {
        this.skills = skills;
        this.objectives = objectives;
        this.catalogNames = catalogNames;
    }

    /** {@code text} with the insight's skill or chapter name in Khmer, for a Khmer reader; otherwise unchanged. */
    @Transactional(readOnly = true)
    public String apply(ParentInsight insight, String text, String language) {
        if (text == null || !UiLanguage.isKhmer(language) || insight.getSupportingData() == null) {
            return text;
        }
        Map<String, Object> data = insight.getSupportingData();
        UUID skillId = id(data.get("skillId"));
        if (skillId != null) {
            return skills.findById(skillId)
                    .map(skill -> swap(text, skill.getName(), catalogNames.of(LocalizableContentType.SKILL, skillId).name()))
                    .orElse(text);
        }
        UUID objectiveId = id(data.get("learningObjectiveId"));
        if (objectiveId != null) {
            return objectives.findById(objectiveId)
                    .map(objective -> swap(text, objective.getTitle(),
                            catalogNames.of(LocalizableContentType.LEARNING_OBJECTIVE, objectiveId).name()))
                    .orElse(text);
        }
        return text;
    }

    private static String swap(String text, String english, String khmer) {
        return khmer == null || english == null || english.isBlank() ? text : text.replace(english, khmer);
    }

    private static UUID id(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
