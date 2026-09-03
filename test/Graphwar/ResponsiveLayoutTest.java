package Graphwar;

/** Small executable check for the responsive UI breakpoints. */
public final class ResponsiveLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check(ResponsiveLayout.columnsForWidth(800) == 1, "small window uses one column");
        check(ResponsiveLayout.columnsForWidth(959) == 1, "narrow breakpoint is stable");
        check(ResponsiveLayout.columnsForWidth(960) == 2, "medium window uses two columns");
        check(ResponsiveLayout.columnsForWidth(1279) == 2, "wide breakpoint is stable");
        check(ResponsiveLayout.columnsForWidth(1280) == 3, "large window uses three columns");
        check(ResponsiveLayout.contentWidth(800) == 752, "content has a small-window gutter");
        check(ResponsiveLayout.contentWidth(1920) == 1200, "content has a readable maximum width");
        check(!ResponsiveLayout.usesSidebar(1099), "medium game layout stacks controls");
        check(ResponsiveLayout.usesSidebar(1100), "wide game layout uses a sidebar");
        System.out.println("responsive-layout-check: PASS");
    }
}
