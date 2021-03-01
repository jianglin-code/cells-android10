#define LOG_TAG "CELLSSERVICE"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <fcntl.h>
#include <errno.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include "CellsPrivateService.h"

#include <binder/IServiceManager.h>
#include <gui/ISurfaceComposer.h>
#include <utils/String16.h>

#include <powermanager/IPowerManager.h>
#include <powermanager/PowerManager.h>

namespace android {

#define SYSTEMPRIVATE_LOGV(x, ...) ALOGV("[CellsPrivate] " x, ##__VA_ARGS__)
#define SYSTEMPRIVATE_LOGD(x, ...) ALOGD("[CellsPrivate] " x, ##__VA_ARGS__)
#define SYSTEMPRIVATE_LOGI(x, ...) ALOGI("[CellsPrivate] " x, ##__VA_ARGS__)
#define SYSTEMPRIVATE_LOGW(x, ...) ALOGW("[CellsPrivate] " x, ##__VA_ARGS__)
#define SYSTEMPRIVATE_LOGE(x, ...) ALOGE("[CellsPrivate] " x, ##__VA_ARGS__)

CellsPrivateService::CellsPrivateService()
{
    setCellstared();
}

CellsPrivateService::~CellsPrivateService()
{
}

int CellsPrivateService::isInCellstar()
{
    return mtar_pthread_t;
}

void CellsPrivateService::setCellstaring()
{
    mtar_pthread_t = 1;
}

void CellsPrivateService::setCellstared()
{
    mtar_pthread_t = 0;
}

static void* tar_pthead(void* o)
{
    CellsPrivateService* pService = (CellsPrivateService*)o;
    if(!pService)
        return NULL;

    errno = 0;
    if ( nice((int)-15) == -1 || errno) {
        ALOGE("Cannot change priority %s (%d)\n",strerror(errno),errno);
    }

    pService->setCellstaring();

    // Delete tar
    system("rm -rf /data/cells/hwcell.img");
        ALOGE("rm -rf /data/cells/hwcell.img \n");

    // Make tar
    //system("cd /data/cells && tar zcvfp hwcell.img --exclude=cell1-rw/data/dalvik-cache --exclude=cell1-rw/data/logs  cell1-rw");
    system("cd /data/cells && tar zcvfp hwcell.img --exclude=cell1-rw/data/dalvik-cache --exclude=cell1-rw/data/cells.logs --exclude=cell1-rw/data/misc/logd  cell1-rw");
        ALOGE("cd /data/cells && tar zcvfp hwcell.img --exclude=cell1-rw/data/dalvik-cache --exclude=cell1-rw/data/cells.logs --exclude=cell1-rw/data/misc/logd  cell1-rw \n");

    pService->setCellstared();
    return (void*)0;
}

void CellsPrivateService::startCellstar()
{
    int ret;
    pthread_t daemon_thread;

    if(isInCellstar())
        return ;

    /* Start listening for connections in a new thread */
    ret = pthread_create(&daemon_thread, NULL,tar_pthead, this);
    if (ret) {
        ALOGE("create_tar_pthread err: %s", strerror(errno));
    }
}

status_t CellsPrivateService::setProperty(const String16& name,const String16& value)
{
    SYSTEMPRIVATE_LOGD("SETPROPERTY arg %s %s", String8(name).string(), String8(value).string());
    status_t result = property_set(String8(name).string(), String8(value).string());
    SYSTEMPRIVATE_LOGD("SETPROPERTY result = %d", result);
    return result;
}

status_t CellsPrivateService::startCellsVM(const String16& name)
{
    name;

    char cmd[200];
    snprintf(cmd, sizeof(cmd), "cellc start %s",String8(name).string());
    SYSTEMPRIVATE_LOGD("STARTCELLSVM cmd = %s", cmd);
    system(cmd);
    return NO_ERROR;
}

status_t CellsPrivateService::stopCellsVM(const String16& name)
{
    name;

    char cmd[200];
    snprintf(cmd, sizeof(cmd), "cellc stop %s",String8(name).string());
    SYSTEMPRIVATE_LOGD("STOPCELLSVM cmd = %s", cmd);
    system(cmd);
    return NO_ERROR;
}

status_t CellsPrivateService::cellsSwitchVM(const String16& name)
{
    name;

    char cmd[200];
    snprintf(cmd, sizeof(cmd), "cellc switch %s",String8(name).string());
    SYSTEMPRIVATE_LOGD("CELLSSWITCHVM cmd = %s", cmd);
    system(cmd);
    property_set("persist.sys.active", String8(name).string());
    return NO_ERROR;
}

status_t CellsPrivateService::cellsSwitchHOST(const String16& name)
{
    name;

    char cmd[200];
    snprintf(cmd, sizeof(cmd), "cellc switch host");
    SYSTEMPRIVATE_LOGD("CELLSSWITCHHOST cmd = %s", cmd);
    system(cmd);
    property_set("persist.sys.active", "");
    return NO_ERROR;
}

static void* gotosleep(void* o)
{
    o;

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::IPowerManager> mPowerManager = 
            android::interface_cast<android::IPowerManager>(sm->checkService(android::String16("power")));
        if(mPowerManager != NULL){
            mPowerManager->goToSleep(long(ns2ms(systemTime())),GO_TO_SLEEP_REASON_POWER_BUTTON,0);
        }
    }

    ALOGD("BACK SYSTEM go to sleep...");

    return (void*)0;
};

static void create_gotosleep_pthread(void)
{
    int ret;
    pthread_t daemon_thread;
    
    /* Start listening for connections in a new thread */
    ret = pthread_create(&daemon_thread, NULL,gotosleep, NULL);
    if (ret) {
        ALOGE("create_gotosleep_pthread err: %s", strerror(errno));
    }
};

status_t CellsPrivateService::switchCellsVM(const String16& name)
{
    int i = 0;
    char value[PROPERTY_VALUE_MAX];
    char pname[PATH_MAX];

    ALOGD("switchCellsVM: %s",String8(name).string());

    property_get("ro.boot.vm", value, "1");
    if((strcmp(value, "0") == 0)){

        {
            sscanf(String8(name).string(), "cell%d",&i);
            if(i <= 0)
                return 0;

            memset(value,0,PROPERTY_VALUE_MAX);
            memset(pname,0,PATH_MAX);
            sprintf(pname, "persist.sys.%s.init",  String8(name).string());
            property_get(pname, value, "0");
            if((strcmp(value, "0") == 0))
                return 0;
        }

        {
            exitHost(android::String16("host"));
        }
    
        {
            cellsSwitchVM(name);
        }
    
        {
            android::sp<android::IServiceManager> sm = android::OtherServiceManager(i);
            android::sp<android::ICellsPrivateService> mCellsPrivateService = 
                android::interface_cast<android::ICellsPrivateService>(sm->checkService(android::String16("CellsPrivateService")));
            if(mCellsPrivateService != NULL){
                mCellsPrivateService->enterCell(name);
            }else{
                SYSTEMPRIVATE_LOGD("OtherServiceManager = 0");
            }
        }
    }else{

        {
            if(strcmp(String8(name).string(), "host") != 0)
                return 0;
        }

        {
            exitCell(name);
        }

        {
            android::sp<android::IServiceManager> sm = android::initdefaultServiceManager();
            android::sp<android::ICellsPrivateService> mCellsPrivateService = 
            android::interface_cast<android::ICellsPrivateService>(sm->checkService(android::String16("CellsPrivateService")));
            if(mCellsPrivateService != NULL){
                mCellsPrivateService->cellsSwitchHOST(android::String16("host"));
            }
        }
    
        {
            android::sp<android::IServiceManager> sm = android::initdefaultServiceManager();
            android::sp<android::ICellsPrivateService> mCellsPrivateService = 
            android::interface_cast<android::ICellsPrivateService>(sm->checkService(android::String16("CellsPrivateService")));
            if(mCellsPrivateService != NULL){
                mCellsPrivateService->enterHost(android::String16("host"));
            }
        }
    }

    {
        create_gotosleep_pthread();
    }

    SYSTEMPRIVATE_LOGD("SWITCHCELLSVM result = %d", 0);
    return NO_ERROR;
}

status_t CellsPrivateService::enterHost(const String16& name)
{
    name;

    {
        property_set("persist.sys.exit", "0");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::IPowerManager> mPowerManager = 
            android::interface_cast<android::IPowerManager>(sm->checkService(android::String16("power")));
        if(mPowerManager != NULL){
            mPowerManager->wakeUp(long(ns2ms(systemTime())),WAKE_REASON_POWER_BUTTON,
                            android::String16("enter_self"),android::String16("CellsPrivateService"));
        }
    }

    {
        property_set("ctl.restart", "adbd");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::ISurfaceComposer> mComposer = 
            android::interface_cast<android::ISurfaceComposer>(sm->checkService(android::String16("SurfaceFlinger")));
        if(mComposer != NULL){
            mComposer->enterSelf();
        }
    }

    {
        //startCellstar();
    }

    SYSTEMPRIVATE_LOGD("ENTERHOST result = %d", 0);
    return NO_ERROR;
}

status_t CellsPrivateService::exitHost(const String16& name)
{
    name;

    {
        property_set("persist.sys.exit", "1");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::ISurfaceComposer> mComposer = 
            android::interface_cast<android::ISurfaceComposer>(sm->checkService(android::String16("SurfaceFlinger")));
        if(mComposer != NULL){
            mComposer->exitSelf();
        }
    }

    {
        property_set("ctl.stop", "adbd");
    }

    SYSTEMPRIVATE_LOGD("EXITHOST result = %d", 0);
    return NO_ERROR;
}

static void write_vm_exit(bool bexit){
    int vmfd = open("/.cell",O_WRONLY);
    if(vmfd>=0){
        if(bexit)
            write(vmfd,"1",strlen("1"));
        else
            write(vmfd,"0",strlen("0"));
        close(vmfd);
    }
}

status_t CellsPrivateService::enterCell(const String16& name)
{
    name;

    {
        write_vm_exit(false);
        property_set("persist.sys.exit", "0");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::IPowerManager> mPowerManager = 
            android::interface_cast<android::IPowerManager>(sm->checkService(android::String16("power")));
        if(mPowerManager != NULL){
            mPowerManager->wakeUp(long(ns2ms(systemTime())),WAKE_REASON_POWER_BUTTON,
                            android::String16("enter_self"),android::String16("CellsPrivateService"));
        }
    }

    {
        property_set("ctl.restart", "adbd");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::ISurfaceComposer> mComposer = 
            android::interface_cast<android::ISurfaceComposer>(sm->checkService(android::String16("SurfaceFlinger")));
        if(mComposer != NULL){
            mComposer->enterSelf();
        }
    }

    SYSTEMPRIVATE_LOGD("ENTERCELL result = %d", 0);
    return NO_ERROR;
}

status_t CellsPrivateService::exitCell(const String16& name)
{
    name;

    {
        write_vm_exit(true);
        property_set("persist.sys.exit", "1");
    }

    {
        android::sp<android::IServiceManager> sm = android::defaultServiceManager();
        android::sp<android::ISurfaceComposer> mComposer = 
            android::interface_cast<android::ISurfaceComposer>(sm->checkService(android::String16("SurfaceFlinger")));
        if(mComposer != NULL){
            mComposer->exitSelf();
        }
    }

    {
        property_set("ctl.stop", "adbd");
    }

    SYSTEMPRIVATE_LOGD("EXITCELL result = %d", 0);
    return NO_ERROR;
}

status_t CellsPrivateService::uploadCellsVM(const String16& name)
{
    name;
    
    // Delete tar
    system("rm -rf /data/cells/hwcell.img");
        ALOGE("rm -rf /data/cells/hwcell.img \n");

    // Make tar
    system("cd /data/cells && busybox tar zcvfp hwcell.img cell1-rw");
        ALOGE("busybox tar zcvfp /data/cells/hwcell.img /data/cells/cell1-rw \n");

    // Upload tar 
    system("cd /data/cells && busybox ftpput -u ftp-hw-user -p 1 129.204.70.73 hwcell.img");
        ALOGE("busybox ftpput -u ftp-hw-user -p 1 129.204.70.73 /data/cells/hwcell.img err=%s \n", strerror(errno));

    return NO_ERROR;
}

status_t CellsPrivateService::downloadCellsVM(const String16& name)
{
    name;

    char cmd[200];

    if(access("/data/cells/hwcell.img",F_OK)!= 0){
        system("touch /data/cells/hwcell.img ");
    }

    // Breakpoint renewal
    system("cd /data/cells && busybox ftpget -c  -u ftp-hw-user -p 1 129.204.70.73 hwcell.img");
        ALOGE("busybox ftpget -u ftp-hw-user -p 1 129.204.70.73 /data/cells/hwcell.img  err=%s \n", strerror(errno));

    // Stop the vm
    snprintf(cmd, sizeof(cmd), "cellc stop cell1");
    system(cmd);
        ALOGE("cellc stop cell1 \n");

    // sleep 2s
    sleep(2);

    // un tar
    system("rm -rf /data/cells/cell1-rw && mkdir /data/cells/cell1-rw");
        ALOGE("rm -rf /data/cells/cell1-rw \n");
    system("cd /data/cells && busybox tar zxvfp hwcell.img");
        ALOGE("busybox tar zxvfp /data/cells/hwcell.img /data/cells/cell1-rw \n");
    return NO_ERROR;
}

status_t CellsPrivateService::untarCellsVM(const String16& name)
{
    name;

    char cmd[200];

    // Get the sdcard gz
    if(access("/data/data/com.cells.cellswitch.secure/hwcell.img",F_OK) != 0){
        return NO_ERROR;
    }

    // un tar
    //system("rm -rf /data/cells/cell1-rw && mkdir /data/cells/cell1-rw");
    //    ALOGE("rm -rf /data/cells/cell1-rw \n");
    system("cd /data/cells && tar zxvfp /data/data/com.cells.cellswitch.secure/hwcell.img");
        ALOGE("cd /data/cells && tar zxvfp /data/data/com.cells.cellswitch.secure/hwcell.img \n");
    system("rm -rf /data/data/com.cells.cellswitch.secure/hwcell.img");
        ALOGE("rm -rf /data/data/com.cells.cellswitch.secure/hwcell.img \n");
    return NO_ERROR;
}

status_t CellsPrivateService::tarCellsVM(const String16& name)
{
    name;

    if(!isInCellstar())
    {
        if(access("/data/cells/hwcell.img",F_OK) != 0){
            startCellstar();
            sleep(2);
        }
    }

    while(isInCellstar())
        sleep(1);

    return NO_ERROR;
}

status_t CellsPrivateService::sendCellsVM(const String16& path, const String16& address)
{
    path;

    int    sock = 0;
    struct sockaddr_in    addr;

    FILE* file = fopen("/data/cells/hwcell.img", "r");
    if(file == NULL){
        ALOGE("sendCellsVM fopen err. ");
        return -1;
    }

    if((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0){
        fclose(file);
        return INVALID_OPERATION;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(10000);
    inet_pton(AF_INET, String8(address).string(), &addr.sin_addr);

    if(connect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0){
        ALOGE("sendCellsVM connect err. ");
        fclose(file);
        return INVALID_OPERATION;
    }

    fseek(file, 0, SEEK_END);
    int filelen = ftell(file);
    if(send(sock, &filelen, sizeof(filelen), 0) < 0){
        close(sock);
        fclose(file);
        ALOGE("send file len  err. ");
        return INVALID_OPERATION;
    }

    rewind(file);

    char buffer[4096] = {0};
    int len = 0, count = 0;
    while((len = fread(buffer, 1, 4096, file)) > 0){
        if(send(sock, buffer, len, 0) < 0){
            close(sock);
            fclose(file);
            ALOGE("sendCellsVM send err. ");
            return INVALID_OPERATION;
        }

        count += len;
    }

    close(sock);
    fclose(file);

    system("rm -rf /data/cells/hwcell.img");
    ALOGD("rm -rf /data/cells/hwcell.img");
    return (count == filelen)?NO_ERROR:UNKNOWN_ERROR;
}

status_t CellsPrivateService::vmSystemReady(const String16& name)
{
    char pname[PATH_MAX] = {0};

    sprintf(pname, "persist.sys.%s.init",  String8(name).string());
    property_set(pname, "1");

    property_set("ctl.restart", "adbd");

    SYSTEMPRIVATE_LOGD("SYSTEMREADY name = %s", String8(name).string());
    return NO_ERROR;
}

};
