package com.platform.learning.service;

import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.ProgressChangeService.ChangeKind;
import org.junit.jupiter.api.Test;

import static com.platform.learner.service.EvidenceStandard.SkillStanding.NEEDS_PRACTICE;
import static com.platform.learner.service.EvidenceStandard.SkillStanding.NOT_STARTED;
import static com.platform.learner.service.EvidenceStandard.SkillStanding.PROGRESSING;
import static com.platform.learner.service.EvidenceStandard.SkillStanding.SECURE;
import static com.platform.learner.service.EvidenceStandard.SkillStanding.TOO_EARLY_TO_TELL;
import static org.assertj.core.api.Assertions.assertThat;

/** How a move between two standings is told (X12): a first judgement is never a fall or a rise. */
class ProgressChangeKindTests {

    @Test
    void eachMoveIsToldForWhatItIs() {
        assertThat(ProgressChangeService.kind(PROGRESSING, SECURE)).isEqualTo(ChangeKind.BECAME_SECURE);
        assertThat(ProgressChangeService.kind(TOO_EARLY_TO_TELL, SECURE)).isEqualTo(ChangeKind.BECAME_SECURE);
        assertThat(ProgressChangeService.kind(NEEDS_PRACTICE, PROGRESSING)).isEqualTo(ChangeKind.MOVED_UP);
        assertThat(ProgressChangeService.kind(TOO_EARLY_TO_TELL, NEEDS_PRACTICE)).isEqualTo(ChangeKind.FIRST_READING);
        assertThat(ProgressChangeService.kind(NOT_STARTED, PROGRESSING)).isEqualTo(ChangeKind.FIRST_READING);
        assertThat(ProgressChangeService.kind(NOT_STARTED, TOO_EARLY_TO_TELL)).isEqualTo(ChangeKind.STARTED);
        assertThat(ProgressChangeService.kind(TOO_EARLY_TO_TELL, TOO_EARLY_TO_TELL)).isEqualTo(ChangeKind.STARTED);
        for (SkillStanding same : new SkillStanding[] {NEEDS_PRACTICE, PROGRESSING, SECURE}) {
            assertThat(ProgressChangeService.kind(same, same)).isEqualTo(ChangeKind.PRACTISED);
        }
        assertThat(ProgressChangeService.kind(SECURE, PROGRESSING)).isEqualTo(ChangeKind.MOVED_DOWN);
        assertThat(ProgressChangeService.kind(PROGRESSING, NEEDS_PRACTICE)).isEqualTo(ChangeKind.MOVED_DOWN);
    }

}
