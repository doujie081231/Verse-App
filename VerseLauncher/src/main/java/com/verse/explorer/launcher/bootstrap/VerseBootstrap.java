package com.verse.explorer.launcher.bootstrap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Permission;
import java.util.Arrays;

public class VerseBootstrap {

    private static volatile boolean securityManagerInstalled = false;

    public static void main(String[] args) {
        try {
            System.loadLibrary("verseexec");
            System.out.println("[VerseBootstrap] verseexec native library loaded");
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[VerseBootstrap] Failed to load verseexec: " + e.getMessage());
        }

        System.out.println("[VerseBootstrap] Starting, args count: " + args.length);
        System.out.println("[VerseBootstrap] java.class.path=" + System.getProperty("java.class.path"));
        System.out.println("[VerseBootstrap] java.library.path=" + System.getProperty("java.library.path"));

        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkExit(int status) {
                    throw new ExitTrappedException(status);
                }

                @Override
                public void checkPermission(Permission perm) {
                }
            });
            securityManagerInstalled = true;
            System.out.println("[VerseBootstrap] SecurityManager installed successfully");
        } catch (UnsupportedOperationException e) {
            System.out.println("[VerseBootstrap] SecurityManager not supported (Java 24+), using shutdown hook fallback");
            installShutdownHookFallback();
        } catch (Exception e) {
            System.out.println("[VerseBootstrap] SecurityManager installation failed: " + e.getMessage());
            installShutdownHookFallback();
        }

        String targetClass = null;
        String[] gameArgs;

        if (args.length > 0 && !args[0].startsWith("-") && args[0].contains(".")) {
            targetClass = args[0];
            gameArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        } else {
            gameArgs = args;
        }

        String[] candidateClasses;
        if (targetClass != null) {
            candidateClasses = new String[]{targetClass, "mio.Wrapper", "net.minecraft.client.main.Main"};
        } else {
            candidateClasses = new String[]{"mio.Wrapper", "net.minecraft.client.main.Main"};
        }

        for (String className : candidateClasses) {
            try {
                System.out.println("[VerseBootstrap] Trying " + className + "...");
                Class<?> cls = Class.forName(className);
                Method mainMethod = cls.getMethod("main", String[].class);
                // mio.Wrapper expects the real main class name as first arg
                String[] argsToPass;
                if (className.equals("mio.Wrapper") && targetClass != null) {
                    argsToPass = new String[gameArgs.length + 1];
                    argsToPass[0] = targetClass;
                    System.arraycopy(gameArgs, 0, argsToPass, 1, gameArgs.length);
                } else {
                    argsToPass = gameArgs;
                }
                System.out.println("[VerseBootstrap] Invoking " + className + ".main() with " + argsToPass.length + " args...");
                mainMethod.invoke(null, (Object) argsToPass);
                System.out.println("[VerseBootstrap] " + className + ".main() returned.");
                return;
            } catch (ClassNotFoundException e) {
                System.out.println("[VerseBootstrap] " + className + " not found, trying next...");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ExitTrappedException) {
                    int exitCode = ((ExitTrappedException) cause).exitCode;
                    System.out.println("[VerseBootstrap] Game exited with code: " + exitCode);
                    return;
                }
                System.out.println("[VerseBootstrap] " + className + " failed: " + cause);
                cause.printStackTrace(System.out);
                return;
            } catch (ExitTrappedException e) {
                System.out.println("[VerseBootstrap] Game exited with code: " + e.exitCode);
                return;
            } catch (Exception e) {
                System.out.println("[VerseBootstrap] " + className + " failed: " + e.getMessage());
                e.printStackTrace(System.out);
                return;
            }
        }

        System.out.println("[VerseBootstrap] No valid main class found");
    }

    private static class ExitTrappedException extends SecurityException {
        final int exitCode;

        ExitTrappedException(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    private static void installShutdownHookFallback() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[VerseBootstrap] JVM shutdown hook triggered");
        }));
        System.out.println("[VerseBootstrap] Shutdown hook installed as fallback");
    }
}
