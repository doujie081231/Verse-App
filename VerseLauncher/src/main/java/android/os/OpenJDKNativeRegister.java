package android.os;

public class OpenJDKNativeRegister {
    static {
        System.loadLibrary("pojavexec");
    }

    public static native void nativeRegisterNatives();
}
