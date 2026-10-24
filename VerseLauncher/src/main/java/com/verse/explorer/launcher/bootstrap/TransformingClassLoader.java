package com.verse.explorer.launcher.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TransformingClassLoader extends ClassLoader {

    private static final String LWJGL_PREFIX = "org.lwjgl.";
    private final Object lock = new Object();

    public TransformingClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (lock) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }

            if (name.startsWith(LWJGL_PREFIX)) {
                try {
                    String resourcePath = name.replace('.', '/') + ".class";
                    InputStream in = getResourceAsStream(resourcePath);
                    if (in != null) {
                        byte[] bytes = readAllBytes(in);
                        Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (Exception e) {
                    // fall through to parent
                }
            }

            return super.loadClass(name, resolve);
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }
}