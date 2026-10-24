#pragma once

#include <string>
#include <vector>
#include <functional>
#include <mutex>
#include <thread>
#include <atomic>

class ProcessManager {
public:
    using OutputCallback = std::function<void(const std::string&)>;
    
    ProcessManager();
    ~ProcessManager();
    
    bool start(const std::string& executable, 
               const std::vector<std::string>& args,
               const std::string& workingDir);
    
    void stop();
    void kill();
    
    bool isRunning() const;
    int getExitCode() const;
    
    void setOutputCallback(OutputCallback callback);
    void setErrorCallback(OutputCallback callback);
    
private:
    void readOutput();
    void readError();
    
    std::atomic<bool> running_;
    std::atomic<int> exitCode_;
    
    std::thread outputThread_;
    std::thread errorThread_;
    
    OutputCallback outputCallback_;
    OutputCallback errorCallback_;
    
    std::mutex callbackMutex_;
    
    void* processHandle_;
    int stdoutPipe_;
    int stderrPipe_;
};
