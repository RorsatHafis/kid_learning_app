package com.platform.content.entity;

/** M5b: who besides its author can find a teacher's lesson. PUBLIC is deferred (D-14). */
public enum LessonVisibility {
    /** The author only. */
    PRIVATE,
    /** Also the teachers and principal of the author's (paid, open) schools, who can copy it into their own content. */
    SCHOOL
}
