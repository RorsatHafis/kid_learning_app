package com.platform.content.entity;

/** Mirrors the {@code content_type} CHECK constraint on {@code content_localizations} (V12, widened in V81). */
public enum LocalizableContentType {

    LESSON_VERSION,
    ACTIVITY_VERSION,
    QUESTION_VERSION,
    /** V81: catalog names (not versioned) - a subject, a skill, a learning objective (chapter), a curriculum. */
    SUBJECT,
    SKILL,
    LEARNING_OBJECTIVE,
    CURRICULUM,
    /** V85: a lesson's title (not versioned), read by teachers and their colleagues. */
    LESSON

}