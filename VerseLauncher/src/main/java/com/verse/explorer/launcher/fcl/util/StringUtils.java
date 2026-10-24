/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.verse.explorer.launcher.fcl.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public final class StringUtils {

    private StringUtils() {}

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter(512);
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }

    public static List<String> tokenize(String str) {
        return tokenize(str, null);
    }

    public static List<String> tokenize(String str, Map<String, String> vars) {
        if (isBlank(str)) return new ArrayList<>();
        if (vars == null) vars = Collections.emptyMap();

        ArrayList<String> parts = new ArrayList<>();
        int varEnd;
        boolean hasValue = false;
        StringBuilder current = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); ) {
            char c = str.charAt(i);
            if (c == '\'') {
                hasValue = true;
                int end = str.indexOf(c, i + 1);
                if (end < 0) end = str.length();
                current.append(str, i + 1, end);
                i = end + 1;
            } else if (c == '"') {
                hasValue = true;
                i++;
                while (i < str.length()) {
                    c = str.charAt(i++);
                    if (c == '"') break;
                    else if (c == '$' && (varEnd = findVarEnd(str, i)) >= 0) {
                        String key = str.substring(i, varEnd);
                        String value = vars.get(key);
                        if (value != null) current.append(value);
                        else current.append('$').append(key);
                        i = varEnd;
                    } else {
                        current.append(c);
                    }
                }
            } else if (c == ' ') {
                if (hasValue) { parts.add(current.toString()); current.setLength(0); hasValue = false; }
                i++;
            } else if (c == '$' && (varEnd = findVarEnd(str, i + 1)) >= 0) {
                hasValue = true;
                String key = str.substring(i + 1, varEnd);
                String value = vars.get(key);
                if (value != null) current.append(value);
                else current.append('$').append(key);
                i = varEnd;
            } else {
                hasValue = true; current.append(c); i++;
            }
        }
        if (hasValue) parts.add(current.toString());
        return parts;
    }

    private static boolean isVarNameStart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
    }
    private static boolean isVarNamePart(char ch) {
        return isVarNameStart(ch) || (ch >= '0' && ch <= '9');
    }
    private static int findVarEnd(String str, int offset) {
        if (offset < str.length() && isVarNameStart(str.charAt(offset))) {
            int end = offset + 1;
            while (end < str.length() && isVarNamePart(str.charAt(end))) end++;
            return end;
        }
        return -1;
    }

    public static String substringAfterLast(String str, char delimiter) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? "" : str.substring(index + 1);
    }

    public static String substringBeforeLast(String str, char delimiter) {
        int index = str.lastIndexOf(delimiter);
        return index == -1 ? str : str.substring(0, index);
    }

    public static String removeSuffix(String str, String suffix) {
        return str.endsWith(suffix) ? str.substring(0, str.length() - suffix.length()) : str;
    }

    public static String removeSuffix(String str, String... suffixes) {
        for (String suffix : suffixes) {
            if (str.endsWith(suffix)) {
                return str.substring(0, str.length() - suffix.length());
            }
        }
        return str;
    }

    public static String addPrefix(String str, String prefix) {
        return str.startsWith(prefix) ? str : prefix + str;
    }
}
