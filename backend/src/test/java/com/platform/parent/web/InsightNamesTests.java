package com.platform.parent.web;

import com.platform.content.entity.LocalizableContentType;
import com.platform.content.service.CatalogNames;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The skill or chapter named in an insight sentence, given in Khmer to a Khmer reader (LOCALIZATION.md, slice 3). */
class InsightNamesTests {

    private final SkillRepository skills = mock(SkillRepository.class);
    private final LearningObjectiveRepository objectives = mock(LearningObjectiveRepository.class);
    private final CatalogNames catalogNames = mock(CatalogNames.class);
    private final InsightNames names = new InsightNames(skills, objectives, catalogNames);

    private final UUID skillId = UUID.randomUUID();
    private final UUID objectiveId = UUID.randomUUID();

    @Test
    void aReviewInsightNamesTheSkillInKhmerForAKhmerReader() {
        when(skills.findById(skillId)).thenReturn(Optional.of(Skill.create(UUID.randomUUID(), "CNT", "Counting", null)));
        when(catalogNames.of(LocalizableContentType.SKILL, skillId)).thenReturn(new CatalogNames.Khmer("ការរាប់", null));
        ParentInsight insight = insight("Counting may benefit from additional practice.", Map.of("skillId", skillId.toString()));

        ParentInsightResponse km = ParentInsightResponse.from(insight, "km", null, names);
        assertThat(km.headline()).isEqualTo("ការរាប់ អាចត្រូវការការហាត់បន្ថែម។");
        ParentInsightResponse en = ParentInsightResponse.from(insight, "en", null, names);
        assertThat(en.headline()).isEqualTo("Counting may benefit from additional practice.");
    }

    @Test
    void anObjectiveInsightNamesTheChapterInKhmerAndAnUntranslatedOneStaysAsWritten() {
        when(objectives.findById(objectiveId)).thenReturn(Optional.of(LearningObjective.create(UUID.randomUUID(), "CH", "Counting things", null)));
        when(catalogNames.of(LocalizableContentType.LEARNING_OBJECTIVE, objectiveId)).thenReturn(new CatalogNames.Khmer("រាប់វត្ថុ", null));
        ParentInsight insight = insight("Your child has mastered Counting things.", Map.of("learningObjectiveId", objectiveId.toString()));
        assertThat(ParentInsightResponse.from(insight, "km", null, names).headline()).isEqualTo("កូនរបស់អ្នកបានស្ទាត់ជំនាញ រាប់វត្ថុ ហើយ។");

        when(catalogNames.of(LocalizableContentType.LEARNING_OBJECTIVE, objectiveId)).thenReturn(CatalogNames.Khmer.NONE);
        assertThat(ParentInsightResponse.from(insight, "km", null, names).headline()).isEqualTo("កូនរបស់អ្នកបានស្ទាត់ជំនាញ Counting things ហើយ។");
    }

    @Test
    void aNameRenamedSinceTheInsightWasRecordedIsLeftAsRecorded() {
        when(skills.findById(skillId)).thenReturn(Optional.of(Skill.create(UUID.randomUUID(), "CNT", "Counting objects", null)));
        when(catalogNames.of(LocalizableContentType.SKILL, skillId)).thenReturn(new CatalogNames.Khmer("ការរាប់", null));
        ParentInsight insight = insight("Counting may benefit from additional practice.", Map.of("skillId", skillId.toString()));
        assertThat(ParentInsightResponse.from(insight, "km", null, names).headline()).isEqualTo("Counting អាចត្រូវការការហាត់បន្ថែម។");
    }

    private static ParentInsight insight(String headline, Map<String, Object> data) {
        return ParentInsight.generate(UUID.randomUUID(), ParentInsightType.RECOMMENDATION, headline, null, data);
    }
}
