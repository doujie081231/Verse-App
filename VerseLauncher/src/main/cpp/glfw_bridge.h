#pragma once

class GLFWBridge {
public:
    GLFWBridge();
    ~GLFWBridge();
    
    bool initialize(int width, int height);
    void shutdown();
    
    void swapBuffers();
    void pollEvents();
    
    bool shouldClose() const;
    
    void setWindowTitle(const char* title);
    void getWindowSize(int* width, int* height) const;
    
private:
    void* window_;
    int width_;
    int height_;
    bool initialized_;
};
