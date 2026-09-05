//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Small persistent progress store for the offline campaign. */
public final class CampaignProgress {
    private static final String COMPLETED_PREFIX = "completed.";
    private static final String STEP_PREFIX = "completed.step.";
    private final Preferences preferences;

    public CampaignProgress() {
        this(Preferences.userNodeForPackage(CampaignProgress.class));
    }

    CampaignProgress(Preferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences");
        }
        this.preferences = preferences;
    }

    public boolean isComplete(String lessonId) {
        return lessonId != null && (preferences.getBoolean(COMPLETED_PREFIX + lessonId, false)
                || (isStepComplete(lessonId, 1) && isStepComplete(lessonId, 2)));
    }

    public void markComplete(String lessonId) {
        if (lessonId == null || lessonId.trim().length() == 0) {
            return;
        }
        preferences.putBoolean(COMPLETED_PREFIX + lessonId, true);
        preferences.putBoolean(stepKey(lessonId, 1), true);
        preferences.putBoolean(stepKey(lessonId, 2), true);
        flush();
    }

    public boolean isStepComplete(String lessonId, int stepNumber) {
        return lessonId != null && stepNumber >= 1 && stepNumber <= 2
                && (preferences.getBoolean(stepKey(lessonId, stepNumber), false)
                        || (stepNumber == 1 && preferences.getBoolean(COMPLETED_PREFIX + lessonId, false)));
    }

    public void markStepComplete(String lessonId, int stepNumber) {
        if (lessonId == null || lessonId.trim().length() == 0 || stepNumber < 1 || stepNumber > 2) {
            return;
        }
        preferences.putBoolean(stepKey(lessonId, stepNumber), true);
        if (isStepComplete(lessonId, 1) && isStepComplete(lessonId, 2)) {
            preferences.putBoolean(COMPLETED_PREFIX + lessonId, true);
        }
        flush();
    }

    public boolean isUnlocked(CampaignLesson[] lessons, int index) {
        if (lessons == null || index < 0 || index >= lessons.length) {
            return false;
        }
        return index == 0 || (lessons[index - 1] != null && isComplete(lessons[index - 1].getId()));
    }

    public int completedCount(CampaignLesson[] lessons) {
        if (lessons == null) {
            return 0;
        }
        int count = 0;
        for (CampaignLesson lesson : lessons) {
            if (lesson != null && isComplete(lesson.getId())) {
                count++;
            }
        }
        return count;
    }

    public void reset() {
        try {
            String[] keys = preferences.keys();
            for (String key : keys) {
                if (key.startsWith(COMPLETED_PREFIX) || key.startsWith(STEP_PREFIX)) {
                    preferences.remove(key);
                }
            }
            flush();
        } catch (BackingStoreException error) {
            throw new IllegalStateException("Could not reset campaign progress", error);
        }
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException error) {
            throw new IllegalStateException("Could not save campaign progress", error);
        }
    }

    private String stepKey(String lessonId, int stepNumber) {
        return STEP_PREFIX + lessonId + "." + stepNumber;
    }
}
