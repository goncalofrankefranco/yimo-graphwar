//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

/** Breakpoints shared by the responsive Swing screens. */
public final class ResponsiveLayout {
    private ResponsiveLayout() {
    }

    public static int columnsForWidth(int width) {
        if (width < 960) {
            return 1;
        }
        if (width < 1280) {
            return 2;
        }
        return 3;
    }

    public static int contentWidth(int width) {
        return Math.max(0, Math.min(1200, width - 48));
    }

    public static boolean usesSidebar(int width) {
        return width >= 1100;
    }
}
