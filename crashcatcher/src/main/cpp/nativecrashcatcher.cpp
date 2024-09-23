#ifdef __cplusplus
extern "C" {
#endif


#include <jni.h>
#include <signal.h>
#include <pthread.h>
#include <android/log.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <string.h>


#define  TAG    "NATIVE_CRASH_CATCHER"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)


/**
 * 关注的信号
 * 信号列表可执行如下命令查看：kill -l
 * 信号详细说明参考：man 7 signal
 * */
static int concernedSignals[] = {
        SIGABRT,
        SIGBUS,
        SIGFPE,
        SIGILL,
        SIGSEGV,
        SIGPIPE,
        SIGTRAP,
        SIGSYS,
        SIGSTKFLT,
        SIGQUIT, // ANR时会产生该信号
};
static const int concernedSignalsSize=sizeof(concernedSignals)/sizeof(concernedSignals[0]);

/**
 * 之前的信号处理
 * 安装信号处理会覆盖之前的信号处理，需要保留之前的以待后续调用
 */
typedef struct SigHandler{
    int sn;
    struct sigaction sa;
}SigHandler;
static SigHandler oldSigHandlers[concernedSignalsSize]={};

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

static JavaVM * jvm;
static jobject uiCallback;

static char sigdetail[4*1024];

static void printThreadInfo(){
    LOGI("thread info: pid %d tid %lu\n", getpid(), gettid());
}


static void execOldSigHandlers(int signo, siginfo_t* info, void* context){
    for (int i=0; i<concernedSignalsSize; ++i){
        SigHandler handler=oldSigHandlers[i];
        if (handler.sn==signo){
            struct sigaction sa = handler.sa;
            if (sa.sa_flags&SA_SIGINFO){
                sa.sa_sigaction(signo, info, context);
            }else{
                sa.sa_handler(signo);
            }
            break;
        }
    }
}


static int setBlockSignals(bool block){
    sigset_t ss;
    sigemptyset(&ss);
    for (int sig : concernedSignals){
        sigaddset(&ss, sig);
    }

    int how=block?SIG_BLOCK:SIG_UNBLOCK;
    LOGI("###### setBlockSignals %d", how);
    int ret = pthread_sigmask(how, &ss, NULL);
    if (ret!=0){
        LOGE("###### setBlockSignals failed %d", ret);
    }
    return ret;
}

static volatile sig_atomic_t handleSigState=0;
static char* assembleCrashInfo(siginfo_t* info);
static void sigHandler(int signo, siginfo_t* info, void* context){
    LOGI("sigHandler IN, signo %d", signo);
    printThreadInfo();
    if (handleSigState == 1){
        LOGI("sigHandler in progress, pause!");
        pause();
    }else if (handleSigState == 2){
        LOGI("sigHandler finished already, exit!");
        exit(66);
    }
    handleSigState=1;

    assembleCrashInfo(info);

    LOGI("notify clean up thread");
    pthread_cond_signal(&cond);

    LOGI("wait clean finish");
    pthread_cond_wait(&cond, &mutex);

    LOGI("execOldSigHandlers");
    execOldSigHandlers(signo, info, context);

    handleSigState=2;
    LOGI("sigHandler OUT, signo %d", signo);
    exit(55);
}


static void callbackToUI(){
    LOGI("callbackToUI IN");
    if (uiCallback == NULL){
        LOGE("uiCallback == NULL");
        return;
    }

    JNIEnv * env = NULL;

    jint ret = jvm->AttachCurrentThread( &env, NULL );
    if (ret!=JNI_OK) {
        LOGE("AttachCurrentThread failed");
        return;
    }

    jclass jCls = env->GetObjectClass(uiCallback );

    jmethodID jMid = env->GetMethodID( jCls, "callback", "(Ljava/lang/String;)V" );
    if( NULL == jMid ){
        LOGE("NULL == jMid");
        return;
    }

    jstring msg=env->NewStringUTF(sigdetail);
    env->CallVoidMethod(uiCallback, jMid, msg);

    env->DeleteLocalRef(jCls);

    jvm->DetachCurrentThread();

    LOGI("callbackToUI OUT");
}


static void* cleanup(void *arg){
    LOGI("cleanup IN");
    prctl(PR_SET_NAME, "nativecrashcatcher");
    printThreadInfo();

    pthread_mutex_lock(&mutex);
    LOGI("cleanup waiting...");
    pthread_cond_wait(&cond, &mutex);
    pthread_mutex_unlock(&mutex);

    callbackToUI();

    // 延迟一会等上层善后完成
//    sleep(2);

    LOGI("cleanup wakeup sighandler");
    pthread_cond_signal(&cond);

    LOGI("cleanup OUT");
    return nullptr;
}


static int setupAltStack() {
    stack_t stack{
            .ss_sp = malloc(SIGSTKSZ),
            .ss_flags = 0,
            .ss_size = SIGSTKSZ,
    };
    if (!stack.ss_sp) {
        LOGE("fail to alloc stack for crash catching");
        return -1;
    }
    if (sigaltstack(&stack, nullptr) != 0) {
        LOGE("fail to setup signal stack");
        return -1;
    }
    return 0;
}


static int setupSigHandlers(){
    struct sigaction act{
            .sa_flags=SA_SIGINFO | SA_ONSTACK,
            .sa_sigaction=sigHandler,
    };
    struct sigaction old_act{};
    for (int i=0; i<concernedSignalsSize; ++i){
        int sig=concernedSignals[i];
        int ret = sigaction(sig, &act, &old_act);
        if (ret != 0){
            LOGE("Fail to set signal handler for signo %d", sig);
            continue;
        }
        SigHandler handler={
                .sn=sig,
                .sa=old_act
        };
        oldSigHandlers[i] = handler;
    }
    return 0;
}


static int setupCleanupThread(){
    LOGI("setupCleanupThread IN");
    printThreadInfo();

    pthread_t tid;
    if (pthread_create(&tid, 0, cleanup, 0) != 0){
        LOGE("###### create jni callback thread failed!");
        return -1;
    }
    return 0;
}


static char* assembleCrashInfo(siginfo_t* info) {
    const char *signo=NULL;
    const char *detail=NULL;
    switch (info->si_signo) {
        case SIGILL:
            signo = "SIGILL";
            switch (info->si_code) {
                case ILL_ILLOPC:
                    detail = "ILL_ILLOPC(illegal opcode)";
                    break;
                case ILL_ILLOPN:
                    detail = "ILL_ILLOPN(illegal operand)";
                    break;
                case ILL_ILLADR:
                    detail = "ILL_ILLADR(illegal addressing mode)";
                    break;
                case ILL_ILLTRP:
                    detail = "ILL_ILLTRP(illegal trap)";
                    break;
                case ILL_PRVOPC:
                    detail = "ILL_PRVOPC(privileged opcode)";
                    break;
                case ILL_PRVREG:
                    detail = "ILL_PRVREG(privileged register)";
                    break;
                case ILL_COPROC:
                    detail = "ILL_COPROC(coprocessor error)";
                    break;
                case ILL_BADSTK:
                    detail = "ILL_BADSTK(internal stack error)";
                    break;
            }
            break;

        case SIGFPE:
            signo = "SIGFPE";
            switch (info->si_code) {
                case FPE_INTDIV:
                    detail = "FPE_INTDIV(integer divide by zero)";
                    break;
                case FPE_INTOVF:
                    detail = "FPE_INTOVF(integer overflow)";
                    break;
                case FPE_FLTDIV:
                    detail = "FPE_FLTDIV(floating-point divide by zero)";
                    break;
                case FPE_FLTOVF:
                    detail = "FPE_FLTOVF(floating-point overflow)";
                    break;
                case FPE_FLTUND:
                    detail = "FPE_FLTUND(floating-point underflow)";
                    break;
                case FPE_FLTRES:
                    detail = "FPE_FLTRES(floating-point inexact result)";
                    break;
                case FPE_FLTINV:
                    detail = "FPE_FLTINV(invalid floating-point operation)";
                    break;
                case FPE_FLTSUB:
                    detail = "FPE_FLTSUB(subscript out of range)";
                    break;
            }
            break;

        case SIGSEGV:
            signo = "SIGSEGV";
            switch (info->si_code) {
                case SEGV_MAPERR:
                    detail = "SEGV_MAPERR(address not mapped to object)";
                    break;
                case SEGV_ACCERR:
                    detail = "SEGV_ACCERR(invalid permissions for mapped object)";
                    break;
            }
            break;

        case SIGBUS:
            signo = "SIGBUS";
            switch (info->si_code) {
                case BUS_ADRALN:
                    detail = "BUS_ADRALN(invalid address alignment)";
                    break;
                case BUS_ADRERR:
                    detail = "BUS_ADRERR(nonexistent physical address)";
                    break;
                case BUS_OBJERR:
                    detail = "BUS_OBJERR(object-specific hardware error)";
                    break;
            }
            break;

        case SIGABRT:
            signo = "SIGABRT";
            break;

        case SIGPIPE:
            signo = "SIGPIPE";
            break;
    }

    for (int i=0; i<sizeof(sigdetail);++i){
        sigdetail[i]=0;
    }
    char buf[32] = {0};
    prctl(PR_GET_NAME, buf);
    pid_t tid=gettid();
    sprintf(sigdetail, "thread=%s(%d),", buf, tid);

    if (signo==NULL){
        sprintf(buf, "signo=%d,",info->si_signo);
    }else{
        sprintf(buf, "%s,",signo);
    }
    strcat(sigdetail, buf);

    if (detail==NULL){
        sprintf(buf, "code=%d", info->si_code);
        strcat(sigdetail, buf);
    }else{
        strcat(sigdetail, detail);
    }

    LOGI("siginfo_t(%d,%d), sigdetail=%s, signo=%s, detail=%s",
         info->si_signo, info->si_code, sigdetail, signo, detail);

    return sigdetail;
}


static void triggerException(int signo) {
    LOGI("triggerException IN signo=%d", signo);
    printThreadInfo();

    switch (signo) {
        case SIGABRT:{
            LOGI("triggerException abort");
            abort();
            break;
        }
        case SIGBUS:{
            // 总线错误
            LOGI("triggerException SIGBUS");
            int arr[16];
            char *p=(char *)&arr;
            int *p2=(int *)(p+1);
            LOGI("p=%d",*p2);
            break;
        }
        case SIGFPE:{
            LOGI("triggerException divide by 0");
            int a=3/0;
            break;
        }
        case SIGSEGV:
        default:{
            // 非法指针解引用
//            LOGI("triggerException nullptr");
//            int *p= nullptr;
//            *p=1;
//            LOGI("a=%d",*p);

            // 数组越界
            LOGI("array out of index");
            int a[2]={1,2};
            a[2]=3;
            LOGI("a[2]=%d",a[2]);

            break;
        }
    }

    LOGI("triggerException OUT");
}


static void* trigger(void *arg){
    prctl(PR_SET_NAME, "triggerException");

//    triggerException(SIGABRT);
    triggerException(SIGSEGV);
//    triggerException(SIGBUS);
//    triggerException(SIGFPE);
}


JNIEXPORT void JNICALL
Java_com_sissi_apm_crashcatcher_CrashCatcher_initNativeCrashCatcher(JNIEnv *env, jclass cls, jobject cb) {

    LOGI("init native crash catcher...");

    if (jvm!=NULL){
        LOGE("has inited already!");
        return;
    }
    env->GetJavaVM(&jvm);

    uiCallback=env->NewGlobalRef(cb);

    setupAltStack();

//    setBlockSignals(true);

    setupSigHandlers();

    setupCleanupThread();
}


//static bool loaded;
//jint JNI_OnLoad(JavaVM * vm, void * reserved)
//{
//    LOGI("load native crash catcher!");
//    printThreadInfo();
//    if (!loaded){
//        loaded=true;
//    }else{
//        LOGE("duplicately load lib, something wrong happen, exit process!");
//        exit(666);
//    }
//    return JNI_VERSION_1_6;
//}

#ifdef __cplusplus
}
#endif
