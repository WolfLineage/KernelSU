#include <jni.h>

#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

#include <fcntl.h>
#include <poll.h>
#include <pty.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <android/log.h>

namespace {
constexpr const char *kTag = "YuSUPty";

struct PtyHandle {
    int master;
    pid_t child;
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vorthas_yusu_ui_screen_terminal_Pty_nativeOpen(
        JNIEnv *env, jclass, jobjectArray argv, jint cols, jint rows) {
    // Marshal all JNI data BEFORE fork(): calling back into the JVM from a forked child
    // can deadlock on monitors owned by other threads.
    std::vector<std::string> args;
    const jsize count = env->GetArrayLength(argv);
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(argv, i));
        if (value == nullptr) continue;
        const char *chars = env->GetStringUTFChars(value, nullptr);
        args.emplace_back(chars != nullptr ? chars : "");
        env->ReleaseStringUTFChars(value, chars);
    }

    int master = -1;
    int slave = -1;
    if (openpty(&master, &slave, nullptr, nullptr, nullptr) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "openpty failed: %s", strerror(errno));
        return 0;
    }

    struct winsize ws = {};
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ioctl(slave, TIOCSWINSZ, &ws);

    pid_t child = fork();
    if (child < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "fork failed: %s", strerror(errno));
        close(master);
        close(slave);
        return 0;
    }

    if (child == 0) {
        prctl(PR_SET_PDEATHSIG, SIGHUP);
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);

        if (!args.empty()) {
            std::vector<char *> cArgs;
            cArgs.reserve(args.size() + 1);
            for (auto &arg : args) cArgs.push_back(const_cast<char *>(arg.c_str()));
            cArgs.push_back(nullptr);
            execvp(cArgs[0], cArgs.data());
        }

        // Fallback for devices where the requested daemon is unavailable.
        const char *shellArgs[] = {"/system/bin/sh", nullptr};
        execvp(shellArgs[0], const_cast<char **>(shellArgs));
        _exit(127);
    }

    close(slave);
    const int flags = fcntl(master, F_GETFL, 0);
    if (flags >= 0) fcntl(master, F_SETFL, flags | O_NONBLOCK);

    auto *handle = new PtyHandle{master, child};
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_vorthas_yusu_ui_screen_terminal_Pty_nativeRead(
        JNIEnv *env, jclass, jlong nativeHandle) {
    auto *handle = reinterpret_cast<PtyHandle *>(nativeHandle);
    if (handle == nullptr || handle->master < 0) return nullptr;

    char buffer[16384];
    const ssize_t count = read(handle->master, buffer, sizeof(buffer));
    if (count > 0) {
        jbyteArray result = env->NewByteArray(static_cast<jsize>(count));
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(count),
                                reinterpret_cast<const jbyte *>(buffer));
        return result;
    }
    if (count == 0 || errno == EIO) return nullptr;
    // EAGAIN: no data right now.
    return env->NewByteArray(0);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_vorthas_yusu_ui_screen_terminal_Pty_nativeWrite(
        JNIEnv *env, jclass, jlong nativeHandle, jbyteArray data) {
    auto *handle = reinterpret_cast<PtyHandle *>(nativeHandle);
    if (handle == nullptr || handle->master < 0 || data == nullptr) return -1;

    const jsize length = env->GetArrayLength(data);
    std::vector<jbyte> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, 0, length, bytes.data());

    size_t written = 0;
    while (written < bytes.size()) {
        struct pollfd pfd = {handle->master, POLLOUT, 0};
        const int ready = poll(&pfd, 1, 250);
        if (ready <= 0) break;
        const ssize_t count = write(handle->master, bytes.data() + written, bytes.size() - written);
        if (count > 0) {
            written += static_cast<size_t>(count);
            continue;
        }
        if (count < 0 && errno == EINTR) continue;
        break;
    }
    return static_cast<jint>(written);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vorthas_yusu_ui_screen_terminal_Pty_nativeResize(
        JNIEnv *, jclass, jlong nativeHandle, jint cols, jint rows) {
    auto *handle = reinterpret_cast<PtyHandle *>(nativeHandle);
    if (handle == nullptr || handle->master < 0) return JNI_FALSE;
    struct winsize ws = {};
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    return ioctl(handle->master, TIOCSWINSZ, &ws) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vorthas_yusu_ui_screen_terminal_Pty_nativeClose(
        JNIEnv *, jclass, jlong nativeHandle) {
    auto *handle = reinterpret_cast<PtyHandle *>(nativeHandle);
    if (handle == nullptr) return;
    if (handle->child > 0) {
        kill(handle->child, SIGHUP);
        kill(handle->child, SIGTERM);
    }
    if (handle->master >= 0) close(handle->master);
    if (handle->child > 0) waitpid(handle->child, nullptr, WNOHANG);
    delete handle;
}
