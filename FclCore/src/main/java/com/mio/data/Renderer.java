package com.mio.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Renderer {

    public static final String ID_GL4ES = "gl4es";
    public static final String ID_NGGL4ES = "nggl4es";
    public static final String ID_VGPU = "vgpu";
    public static final String ID_VIRGL = "virgl";
    public static final String ID_ZINK = "zink";
    public static final String ID_FREEDRENO = "freedreno";

    public static final Renderer GLES = new Renderer(
            "opengles3",
            "OpenGL ES 3.x (Default)",
            "",
            "libEGL.so",
            "libGLESv2.so",
            Collections.emptyList()
    );

    private static final List<Renderer> RENDERER_LIST = Arrays.asList(
            GLES,
            new Renderer(ID_GL4ES, "GL4ES", "", "libEGL.so", "libgl4es_114.so",
                    Arrays.asList("LIBGL_ES=2", "LIBGL_MIPMAP=3", "LIBGL_NORMALIZE=1", "LIBGL_NOERROR=1",
                            "LIBGL_NAME=libgl4es_114.so", "LIBEGL_NAME=libEGL.so")),
            new Renderer(ID_NGGL4ES, "NGGL4ES", "", "libEGL.so", "libng_gl4es.so",
                    Arrays.asList("LIBGL_USE_MC_COLOR=1", "LIBGL_GL=31", "LIBGL_ES=3",
                            "LIBGL_NORMALIZE=1", "LIBGL_NOERROR=1",
                            "LIBGL_NAME=libng_gl4es.so", "LIBEGL_NAME=libEGL.so")),
            new Renderer(ID_VGPU, "VGPU", "", "libEGL.so", "libvgpu.so",
                    Arrays.asList("LIBGL_ES=2", "LIBGL_MIPMAP=3", "LIBGL_NORMALIZE=1", "LIBGL_NOERROR=1",
                            "LIBGL_NAME=libvgpu.so", "LIBEGL_NAME=libEGL.so")),
            new Renderer(ID_VIRGL, "VirGL", "", "libEGL.so", "libOSMesa_81.so",
                    Arrays.asList("MESA_GL_VERSION_OVERRIDE=4.3", "MESA_GLSL_VERSION_OVERRIDE=430")),
            new Renderer(ID_ZINK, "Zink", "", "libEGL_mesa.so", "libglxshim.so",
                    Arrays.asList("MESA_GL_VERSION_OVERRIDE=4.6", "MESA_GLSL_VERSION_OVERRIDE=460",
                            "MESA_LOADER_DRIVER_OVERRIDE=zink")),
            new Renderer(ID_FREEDRENO, "Freedreno", "", "libEGL.so", "libOSMesa_8.so",
                    Collections.emptyList())
    );

    private final String id;
    private final String name;
    private final String path;
    private final String eglName;
    private final String glName;
    private final List<String> pojavEnv;

    public Renderer(String id, String name, String path, String eglName, String glName, List<String> pojavEnv) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.eglName = eglName;
        this.glName = glName;
        this.pojavEnv = pojavEnv;
    }

    public static List<Renderer> getRendererList() {
        return new ArrayList<>(RENDERER_LIST);
    }

    public static Renderer valueOf(String id) {
        for (Renderer renderer : RENDERER_LIST) {
            if (renderer.id.equals(id)) {
                return renderer;
            }
        }
        return GLES;
    }

    public static Renderer getSelected() {
        return GLES;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getEglName() {
        return eglName;
    }

    public String getGlName() {
        return glName;
    }

    public String getGLPath() {
        if (!path.isEmpty()) {
            return path + "/" + glName;
        }
        return glName;
    }

    public List<String> getPojavEnv() {
        return pojavEnv;
    }

    public boolean isEqual(String id) {
        return this.id.equals(id);
    }
}
