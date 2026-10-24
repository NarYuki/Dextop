#include <jni.h>
#include <android/hardware_buffer.h>
#include <dlfcn.h>
#include <stdint.h>
#include <sys/socket.h>
#include <unistd.h>

JNIEXPORT jobjectArray JNICALL Java_WaylandPresentationProbe_directBuffer(JNIEnv *env, jclass cls,
    jintArray layout, jboolean blue) {
    AHardwareBuffer_Desc desc = {.width=32,.height=32,.layers=1,.format=1,
        .usage=AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT |
            AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN};
    AHardwareBuffer *buffer=NULL;
    if(AHardwareBuffer_allocate(&desc,&buffer)) return NULL;
    void *pixels=NULL;
    if(AHardwareBuffer_lock(buffer,AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,-1,NULL,&pixels)) {
        AHardwareBuffer_release(buffer);return NULL;
    }
    AHardwareBuffer_describe(buffer,&desc);
    for(unsigned y=0;y<32;y++) for(unsigned x=0;x<32;x++)
        ((uint32_t *)pixels)[y*desc.stride+x]=blue ? 0xffff0000u : 0xff0000ffu;
    AHardwareBuffer_unlock(buffer,NULL);
    void *lib=dlopen("libnativewindow.so",RTLD_NOW|RTLD_LOCAL);
    const void *(*get_handle)(const AHardwareBuffer *)=lib ? dlsym(lib,"AHardwareBuffer_getNativeHandle") : NULL;
    struct handle {int version,nfds,nints,data[];};
    const struct handle *handle=get_handle ? get_handle(buffer) : NULL;
    int fd=handle && handle->nfds>0 ? dup(handle->data[0]) : -1;
    if(lib) dlclose(lib);
    int pair[2];
    if(fd<0 || socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair)) {
        if(fd>=0)close(fd);AHardwareBuffer_release(buffer);return NULL;
    }
    if(AHardwareBuffer_sendHandleToUnixSocket(buffer,pair[0])) {
        close(pair[0]);close(pair[1]);close(fd);AHardwareBuffer_release(buffer);return NULL;
    }
    close(pair[0]);AHardwareBuffer_release(buffer);
    jint stride=desc.stride;
    (*env)->SetIntArrayRegion(env,layout,0,1,&stride);
    jclass type=(*env)->FindClass(env,"java/io/FileDescriptor");
    jmethodID init=(*env)->GetMethodID(env,type,"<init>","()V");
    jfieldID descriptor=(*env)->GetFieldID(env,type,"descriptor","I");
    jobjectArray result=(*env)->NewObjectArray(env,2,type,NULL);
    int values[]={fd,pair[1]};
    for(int i=0;i<2;i++) {
        jobject object=(*env)->NewObject(env,type,init);
        (*env)->SetIntField(env,object,descriptor,values[i]);
        (*env)->SetObjectArrayElement(env,result,i,object);
        (*env)->DeleteLocalRef(env,object);
    }
    return result;
}
