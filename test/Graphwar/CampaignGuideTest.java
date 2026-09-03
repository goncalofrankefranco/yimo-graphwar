package Graphwar;

import java.lang.reflect.Method;

/** Ensures lessons teach construction instead of pre-filling the solution. */
public final class CampaignGuideTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String guide(CampaignLesson lesson) {
        try {
            Method method = CampaignLesson.class.getDeclaredMethod("getGuide");
            method.setAccessible(true);
            return (String) method.invoke(lesson);
        } catch (Exception error) {
            return null;
        }
    }

    private static String startingExpression(CampaignLesson lesson) {
        try {
            Method method = CampaignScreen.class.getDeclaredMethod("startingExpression", CampaignLesson.class);
            method.setAccessible(true);
            return (String) method.invoke(null, lesson);
        } catch (Exception error) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson[] lessons = CampaignLesson.loadAll(CampaignScreen.class);
        for (CampaignLesson lesson : lessons) {
            String guide = guide(lesson);
            check(guide != null && guide.trim().length() > 0,
                    "every lesson must explain how to construct its function");
            String expression = startingExpression(lesson);
            check(expression != null && !lesson.getFunction().equals(expression),
                    "the lesson must not pre-fill its answer");
        }
        System.out.println("campaign-guide-check: PASS");
    }
}
