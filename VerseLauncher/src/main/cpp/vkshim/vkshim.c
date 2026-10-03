// vkshim —— 系统 Vulkan 加载器包装层。
// 启动器检测到设备存在驱动能力缺口时（VKSHIM_ENABLE=1）经本层加载，按缺口类型合成：
// 一、驱动缺失 VK_EXT_vertex_attribute_divisor 但提供 VK_KHR_vertex_attribute_divisor：
//   1. vkEnumerateDeviceExtensionProperties 补报 EXT 扩展；
//   2. vkCreateDevice 把启用列表里的 EXT 换成 KHR；EXT v1/v2 应用不传 features 结构，
//      而 KHR/核心把 divisor 用法门控在其后，链上缺位时补写启用节点
//      （两个特性位按驱动实际报告的支持情况置位）；
//   3. vkGetPhysicalDeviceProperties2 把 EXT properties 节点转接为 KHR 节点
//      （KHR 结构多一个 supportsNonZeroFirstInstance 字段，须用自有存储承接后回填）。
//   features（sType 1000190002）与 pipeline divisor state（sType 1000190001）的 sType
//   在注册表中 KHR 与 EXT 同值，经包装后可直达驱动，无需改写。
//   驱动原生带有 EXT 时全链路直通。
// 二、驱动核心能力位 fillModeNonSolid 缺失（线框/点填充多边形模式）：模拟为降级运行——
//   1. vkGetPhysicalDeviceFeatures(2) 谎报该能力位为开启，保证应用与游戏侧检测通过；
//   2. vkCreateDevice 时从启用 features（含 pNext 链 VkPhysicalDeviceFeatures2）剥除该位，
//      避免真驱动拒绝设备创建；调用后恢复应用内存；
//   3. vkCreateGraphicsPipelines 把非 VK_POLYGON_MODE_FILL 的管线降级为 FILL：
//      线框语义让位于可运行性（与 VulkanFix-Renderer 的 fill_mode_non_solid 模块同策略）。
//   能力探测针对每个物理设备独立进行，原生支持则全链路直通。

#include <vulkan/vulkan.h>

#include <dlfcn.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "log.h"

#if defined(__aarch64__) || defined(__x86_64__)
#define SYSTEM_VULKAN_LOADER_PATH "/system/lib64/libvulkan.so"
#else
#define SYSTEM_VULKAN_LOADER_PATH "/system/lib/libvulkan.so"
#endif

#define EXT_SPEC_VERSION 3
#define MAX_TRACKED_PHYSICAL_DEVICES 8
#define MAX_TRACKED_DEVICES 16

// 驱动能力判定：
// translate 表示 EXT divisor 需经 KHR 合成；add_khr 表示设备创建时需补上 KHR 扩展名；
// fns_emulated 表示核心能力位 fillModeNonSolid 原生缺失，需模拟（谎报 + 管线降级）；
// divisor_feature / zero_divisor_feature 为驱动对两个 divisor 特性位的报告值，
// 启用驱动不支持的位会让设备创建失败（VK_ERROR_FEATURE_NOT_PRESENT）
struct driver_mode {
    bool translate;
    bool add_khr;
    bool fns_emulated;
    bool divisor_feature;
    bool zero_divisor_feature;
};

// 已创建的逻辑设备是否处于 fillModeNonSolid 模拟（管线降级只对这些设备生效）
static VkDevice g_tracked_devices[MAX_TRACKED_DEVICES];
static bool g_device_fns_emulated[MAX_TRACKED_DEVICES];
static int g_tracked_device_count;

// 与 VkBaseOutStructure ABI 相同，pNext 用 void* 便于链改写
struct chain_node {
    VkStructureType sType;
    void* pNext;
};

static void* g_loader;
static bool g_ready;

static PFN_vkCreateInstance real_create_instance;
static PFN_vkDestroyInstance real_destroy_instance;
static PFN_vkEnumerateInstanceExtensionProperties real_enumerate_instance_extension_properties;
static PFN_vkEnumerateInstanceLayerProperties real_enumerate_instance_layer_properties;
static PFN_vkEnumerateInstanceVersion real_enumerate_instance_version;
static PFN_vkGetInstanceProcAddr real_get_instance_proc_addr;
static PFN_vkGetDeviceProcAddr real_get_device_proc_addr;

// 加载器返回的实例级桩按传入句柄分派，跨实例通用，解析一次即可
static struct {
    PFN_vkEnumeratePhysicalDevices enumerate_physical_devices;
    PFN_vkEnumerateDeviceExtensionProperties enumerate_device_extension_properties;
    PFN_vkGetPhysicalDeviceFeatures get_physical_device_features;
    PFN_vkGetPhysicalDeviceFeatures2 get_physical_device_features2;
    PFN_vkGetPhysicalDeviceProperties get_physical_device_properties;
    PFN_vkGetPhysicalDeviceProperties2 get_physical_device_properties2;
    PFN_vkGetPhysicalDeviceProperties2KHR get_physical_device_properties2_khr;
    PFN_vkCreateDevice create_device;
    // device 级命令，经实例级 gipa 解析到的 trampoline，按传入 device 分派
    PFN_vkCreateGraphicsPipelines create_graphics_pipelines;
} real;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static VkPhysicalDevice g_physical_devices[MAX_TRACKED_PHYSICAL_DEVICES];
static struct driver_mode g_physical_device_modes[MAX_TRACKED_PHYSICAL_DEVICES];
static int g_physical_device_count;

static VkResult shim_enumerate_physical_devices(VkInstance instance, uint32_t* pPhysicalDeviceCount,
                                                VkPhysicalDevice* pPhysicalDevices);
static VkResult shim_enumerate_device_extension_properties(VkPhysicalDevice physicalDevice, const char* pLayerName,
                                                           uint32_t* pPropertyCount,
                                                           VkExtensionProperties* pProperties);
static void shim_get_physical_device_properties2(VkPhysicalDevice physicalDevice,
                                                 VkPhysicalDeviceProperties2* pProperties);
static void shim_get_physical_device_properties2_khr(VkPhysicalDevice physicalDevice,
                                                     VkPhysicalDeviceProperties2* pProperties);
static void shim_get_physical_device_features(VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures* pFeatures);
static void shim_get_physical_device_features2(VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures2* pFeatures);
static void shim_get_physical_device_features2_khr(VkPhysicalDevice physicalDevice,
                                                   VkPhysicalDeviceFeatures2* pFeatures);
static VkResult shim_create_device(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo,
                                   const VkAllocationCallbacks* pAllocator, VkDevice* pDevice);
static VkResult shim_create_graphics_pipelines(VkDevice device, VkPipelineCache pipelineCache, uint32_t createInfoCount,
                                               const VkGraphicsPipelineCreateInfo* pCreateInfos,
                                               const VkAllocationCallbacks* pAllocator, VkPipeline* pPipelines);

static bool extension_list_contains(const VkExtensionProperties* list, uint32_t count, const char* name) {
    for (uint32_t i = 0; i < count; i++) {
        if (strcmp(list[i].extensionName, name) == 0) return true;
    }
    return false;
}

// 链上是否存在指定 sType 的节点，存在时返回首个匹配节点
static struct chain_node* chain_find(const void* pNext, VkStructureType sType) {
    struct chain_node* node = (struct chain_node*) pNext;
    while (node != NULL) {
        if (node->sType == sType) return node;
        node = (struct chain_node*) node->pNext;
    }
    return NULL;
}

// 把 node 接到 *head_link 链尾，返回原链尾的链接槽（原值必为 NULL），调用后须将其置回 NULL 摘除
static void** chain_splice_tail(void** head_link, void* node) {
    struct chain_node* last = *head_link;
    if (last == NULL) {
        *head_link = node;
        return head_link;
    }
    while (last->pNext != NULL) last = (struct chain_node*) last->pNext;
    last->pNext = node;
    return &last->pNext;
}

static struct driver_mode compute_driver_mode(VkPhysicalDevice physicalDevice) {
    struct driver_mode mode = {false, false, false, false, false};
    if (real.enumerate_device_extension_properties == NULL) return mode;

    uint32_t count = 0;
    if (real.enumerate_device_extension_properties(physicalDevice, NULL, &count, NULL) != VK_SUCCESS) {
        return mode;
    }
    VkExtensionProperties* list = NULL;
    if (count > 0) {
        list = malloc(count * sizeof(VkExtensionProperties));
        if (list == NULL ||
            real.enumerate_device_extension_properties(physicalDevice, NULL, &count, list) != VK_SUCCESS) {
            free(list);
            return mode;
        }
    }
    bool hasExt = extension_list_contains(list, count, VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME);
    bool hasKHR = extension_list_contains(list, count, VK_KHR_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME);
    free(list);

    if (hasExt) return mode;
    // 仅在驱动声明 KHR 扩展时合成；经启动器检测门控后本函数只应在该形态下被启用，
    // 无 KHR 扩展名的设备（含仅 Vulkan 1.3 核心功能者）不做合成
    if (hasKHR) {
        mode.translate = true;
        mode.add_khr = true;
        // 补写启用节点前先取驱动报告的特性位；KHR divisor 的最低要求为 Vulkan 1.1，
        // 核心查询接口在该版本必然可用，不可用时仅启用 divisor 功能本身、零 divisor 位关闭
        if (real.get_physical_device_features2 != NULL) {
            VkPhysicalDeviceVertexAttributeDivisorFeaturesKHR divisorFeatures = {
                    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_KHR,
                    NULL,
                    VK_FALSE,
                    VK_FALSE,
            };
            VkPhysicalDeviceFeatures2 features2 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
            features2.pNext = &divisorFeatures;
            real.get_physical_device_features2(physicalDevice, &features2);
            mode.divisor_feature = divisorFeatures.vertexAttributeInstanceRateDivisor == VK_TRUE;
            mode.zero_divisor_feature = divisorFeatures.vertexAttributeInstanceRateZeroDivisor == VK_TRUE;
        } else {
            mode.divisor_feature = true;
        }
    }
    return mode;
}

// fillModeNonSolid 原生支持探测：核心能力位与扩展不同，没有可转接的等价接口，
// 缺失时走降级模拟（谎报 + 管线 fill mode 回退为 FILL），原生支持则直通
static bool compute_fns_emulated(VkPhysicalDevice physicalDevice) {
    if (real.get_physical_device_features == NULL) return false;
    VkPhysicalDeviceFeatures feat;
    real.get_physical_device_features(physicalDevice, &feat);
    return feat.fillModeNonSolid == VK_FALSE;
}

// 查询并登记物理设备的驱动模式；重复查询幂等
static struct driver_mode driver_mode_of(VkPhysicalDevice physicalDevice) {
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < g_physical_device_count; i++) {
        if (g_physical_devices[i] == physicalDevice) {
            struct driver_mode cached = g_physical_device_modes[i];
            pthread_mutex_unlock(&g_lock);
            return cached;
        }
    }
    pthread_mutex_unlock(&g_lock);
    // compute 经真实加载器查询，放在锁外，避免与真驱动内部的锁重入；
    // 并发首查由登记前的重查兜底（重复登记无害但避免）
    struct driver_mode mode = compute_driver_mode(physicalDevice);
    mode.fns_emulated = compute_fns_emulated(physicalDevice);
    FCL_LOG("vkshim: pd %p mode translate=%d add_khr=%d fns_emulated=%d divisor_feat=%d zero_divisor_feat=%d",
            physicalDevice, mode.translate, mode.add_khr, mode.fns_emulated, mode.divisor_feature,
            mode.zero_divisor_feature);
    pthread_mutex_lock(&g_lock);
    bool registered = false;
    for (int i = 0; i < g_physical_device_count; i++) {
        if (g_physical_devices[i] == physicalDevice) registered = true;
    }
    if (!registered && g_physical_device_count < MAX_TRACKED_PHYSICAL_DEVICES) {
        g_physical_devices[g_physical_device_count] = physicalDevice;
        g_physical_device_modes[g_physical_device_count] = mode;
        g_physical_device_count++;
    }
    pthread_mutex_unlock(&g_lock);
    return mode;
}

// 解析实例级真实函数指针。加载器返回的桩按传入句柄分派，对任意 VkInstance /
// VkPhysicalDevice 均有效，故除 vkCreateInstance 外还可由任一包装入口惰性触发
// （应用可能经 gipa(NULL, "vkCreateInstance") 拿到真加载器的创建函数，绕过本 shim）
static void ensure_instance_procs(const void* dispatchable) {
    if (dispatchable == NULL || real.create_device != NULL) return;
    pthread_mutex_lock(&g_lock);
    if (real.create_device == NULL) {
        real.enumerate_physical_devices = (PFN_vkEnumeratePhysicalDevices) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkEnumeratePhysicalDevices");
        real.enumerate_device_extension_properties = (PFN_vkEnumerateDeviceExtensionProperties)
                real_get_instance_proc_addr((VkInstance) dispatchable, "vkEnumerateDeviceExtensionProperties");
        real.get_physical_device_features = (PFN_vkGetPhysicalDeviceFeatures) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkGetPhysicalDeviceFeatures");
        real.get_physical_device_features2 = (PFN_vkGetPhysicalDeviceFeatures2) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkGetPhysicalDeviceFeatures2");
        real.get_physical_device_properties = (PFN_vkGetPhysicalDeviceProperties) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkGetPhysicalDeviceProperties");
        real.get_physical_device_properties2 = (PFN_vkGetPhysicalDeviceProperties2) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkGetPhysicalDeviceProperties2");
        real.get_physical_device_properties2_khr = (PFN_vkGetPhysicalDeviceProperties2KHR)
                real_get_instance_proc_addr((VkInstance) dispatchable, "vkGetPhysicalDeviceProperties2KHR");
        real.create_device = (PFN_vkCreateDevice) real_get_instance_proc_addr((VkInstance) dispatchable,
                                                                              "vkCreateDevice");
        // device 级 trampoline，按后续传入的 device 分派，全局解析一次即可
        real.create_graphics_pipelines = (PFN_vkCreateGraphicsPipelines) real_get_instance_proc_addr(
                (VkInstance) dispatchable, "vkCreateGraphicsPipelines");
    }
    pthread_mutex_unlock(&g_lock);
}

// VerseX M1：pipeline 对象级缓存（实现见文件末尾）
static bool vsx_cache_enabled;
static void vsx_cache_init(void);
static void vsx_entry_destroy_count(VkGraphicsPipelineCreateInfo* infos, uint32_t count);
static VkResult vsx_cache_lookup(VkDevice device, const VkGraphicsPipelineCreateInfo* pCreateInfos,
                                 uint32_t createInfoCount, VkPipeline* pPipelines);
static void vsx_cache_store(VkDevice device, const VkGraphicsPipelineCreateInfo* pCreateInfos,
                            uint32_t createInfoCount, const VkPipeline* pPipelines);
static VkResult shim_destroy_pipeline(VkDevice device, VkPipeline pipeline, const VkAllocationCallbacks* pAllocator);

static void __attribute__((constructor)) vkshim_init(void) {
    // 测试与诊断用：允许指定被包装的加载器路径
    const char* overridePath = getenv("VKSHIM_LOADER_PATH");
    const char* path = (overridePath != NULL && overridePath[0] != '\0') ? overridePath
                                                                         : SYSTEM_VULKAN_LOADER_PATH;
    g_loader = dlopen(path, RTLD_LAZY | RTLD_LOCAL);
    if (g_loader == NULL && overridePath == NULL) {
        g_loader = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    }
    if (g_loader == NULL) {
        FCL_LOG("vkshim: failed to load vulkan loader: %s", dlerror());
        return;
    }

    real_create_instance = (PFN_vkCreateInstance) dlsym(g_loader, "vkCreateInstance");
    real_destroy_instance = (PFN_vkDestroyInstance) dlsym(g_loader, "vkDestroyInstance");
    real_enumerate_instance_extension_properties =
            (PFN_vkEnumerateInstanceExtensionProperties) dlsym(g_loader, "vkEnumerateInstanceExtensionProperties");
    real_enumerate_instance_layer_properties =
            (PFN_vkEnumerateInstanceLayerProperties) dlsym(g_loader, "vkEnumerateInstanceLayerProperties");
    real_enumerate_instance_version = (PFN_vkEnumerateInstanceVersion) dlsym(g_loader, "vkEnumerateInstanceVersion");
    real_get_instance_proc_addr = (PFN_vkGetInstanceProcAddr) dlsym(g_loader, "vkGetInstanceProcAddr");
    real_get_device_proc_addr = (PFN_vkGetDeviceProcAddr) dlsym(g_loader, "vkGetDeviceProcAddr");

    g_ready = real_create_instance != NULL && real_get_instance_proc_addr != NULL &&
              real_enumerate_instance_extension_properties != NULL;
    FCL_LOG("vkshim: wrapping %s, ready=%d", path, g_ready);

    // VerseX M1：pipeline 对象级缓存初始化（默认开启，环境变量可关）
    vsx_cache_init();
}

static VkResult shim_enumerate_device_extension_properties(VkPhysicalDevice physicalDevice, const char* pLayerName,
                                                           uint32_t* pPropertyCount,
                                                           VkExtensionProperties* pProperties) {
    ensure_instance_procs(physicalDevice);
    if (pLayerName != NULL) {
        return real.enumerate_device_extension_properties(physicalDevice, pLayerName, pPropertyCount, pProperties);
    }
    if (real.enumerate_device_extension_properties == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    struct driver_mode mode = driver_mode_of(physicalDevice);
    if (!mode.translate) {
        return real.enumerate_device_extension_properties(physicalDevice, NULL, pPropertyCount, pProperties);
    }

    uint32_t realCount = 0;
    VkResult result = real.enumerate_device_extension_properties(physicalDevice, NULL, &realCount, NULL);
    if (result != VK_SUCCESS && result != VK_INCOMPLETE) return result;

    VkExtensionProperties* realList = NULL;
    if (realCount > 0) {
        realList = malloc(realCount * sizeof(VkExtensionProperties));
        if (realList == NULL) return VK_ERROR_OUT_OF_HOST_MEMORY;
        result = real.enumerate_device_extension_properties(physicalDevice, NULL, &realCount, realList);
        if (result != VK_SUCCESS && result != VK_INCOMPLETE) {
            free(realList);
            return result;
        }
    }

    bool native = extension_list_contains(realList, realCount, VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME);
    uint32_t total = native ? realCount : realCount + 1;

    if (pProperties == NULL) {
        *pPropertyCount = total;
        free(realList);
        return VK_SUCCESS;
    }

    uint32_t limit = *pPropertyCount < total ? *pPropertyCount : total;
    uint32_t fromReal = limit < realCount ? limit : realCount;
    if (fromReal > 0) memcpy(pProperties, realList, fromReal * sizeof(VkExtensionProperties));
    if (!native && limit > fromReal) {
        VkExtensionProperties* entry = &pProperties[fromReal];
        strcpy(entry->extensionName, VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME);
        entry->specVersion = EXT_SPEC_VERSION;
    }
    *pPropertyCount = limit;
    free(realList);
    return limit < total ? VK_INCOMPLETE : VK_SUCCESS;
}

static void translate_properties2(VkPhysicalDevice physicalDevice, VkPhysicalDeviceProperties2* pProperties,
                                  PFN_vkGetPhysicalDeviceProperties2 realFn) {
    ensure_instance_procs(physicalDevice);
    struct driver_mode mode = driver_mode_of(physicalDevice);
    if (!mode.translate || pProperties == NULL) {
        realFn(physicalDevice, pProperties);
        return;
    }

    // EXT properties 结构比 KHR 少一个 supportsNonZeroFirstInstance 字段，
    // 不能原地改 sType 让驱动直写，须用自有 KHR 存储承接后回填
    void** link = &pProperties->pNext;
    VkPhysicalDeviceVertexAttributeDivisorPropertiesEXT* ext = NULL;
    while (*link != NULL) {
        struct chain_node* node = *link;
        if (node->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_PROPERTIES_EXT) {
            ext = (VkPhysicalDeviceVertexAttributeDivisorPropertiesEXT*) node;
            break;
        }
        link = &node->pNext;
    }
    if (ext == NULL) {
        realFn(physicalDevice, pProperties);
        return;
    }

    VkPhysicalDeviceVertexAttributeDivisorPropertiesKHR khr;
    khr.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_PROPERTIES_KHR;
    khr.pNext = ext->pNext;
    khr.maxVertexAttribDivisor = 0;
    khr.supportsNonZeroFirstInstance = VK_FALSE;

    *link = &khr;
    realFn(physicalDevice, pProperties);
    ext->maxVertexAttribDivisor = khr.maxVertexAttribDivisor;
    *link = ext;
}

// 登记 fillModeNonSolid 模拟的逻辑设备；表满时不再跟踪（该设备管线不降级，极端边界）
static void register_device_fns_emulation(VkDevice device, bool fnsEmulated) {
    if (!fnsEmulated) return;
    pthread_mutex_lock(&g_lock);
    bool registered = false;
    for (int i = 0; i < g_tracked_device_count; i++) {
        if (g_tracked_devices[i] == device) registered = true;
    }
    if (!registered && g_tracked_device_count < MAX_TRACKED_DEVICES) {
        g_tracked_devices[g_tracked_device_count] = device;
        g_device_fns_emulated[g_tracked_device_count] = true;
        g_tracked_device_count++;
    }
    pthread_mutex_unlock(&g_lock);
}

static bool device_fns_emulated(VkDevice device) {
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < g_tracked_device_count; i++) {
        if (g_tracked_devices[i] == device) {
            pthread_mutex_unlock(&g_lock);
            return true;
        }
    }
    pthread_mutex_unlock(&g_lock);
    return false;
}

static VkResult shim_create_device(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo,
                                   const VkAllocationCallbacks* pAllocator, VkDevice* pDevice) {
    ensure_instance_procs(physicalDevice);
    struct driver_mode mode = driver_mode_of(physicalDevice);
    if (!mode.translate && !mode.fns_emulated) {
        return real.create_device(physicalDevice, pCreateInfo, pAllocator, pDevice);
    }

    bool extEnabled = false;
    bool khrEnabled = false;
    for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount; i++) {
        const char* name = pCreateInfo->ppEnabledExtensionNames[i];
        if (strcmp(name, VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME) == 0) {
            extEnabled = true;
        } else if (strcmp(name, VK_KHR_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME) == 0) {
            khrEnabled = true;
        }
    }

    // fillModeNonSolid 模拟：应用因谎报而在启用列表中带上该能力位，真驱动会拒绝
    // 设备创建，须以副本剥除（pEnabledFeatures 可能与 pNext 链内 Features2 别名，
    // 统一改为指向自有副本）；pNext 链内的 Features2 原位改写，调用后恢复原值
    VkPhysicalDeviceFeatures patchedFeatures;
    bool featuresCopied = false;
    VkPhysicalDeviceFeatures2* chainedFeatures = NULL;
    VkBool32 chainedOriginalFillMode = VK_FALSE;
    if (mode.fns_emulated) {
        void* curr = (void*) pCreateInfo->pNext;
        while (curr != NULL) {
            VkBaseOutStructure* node = (VkBaseOutStructure*) curr;
            if (node->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2) {
                chainedFeatures = (VkPhysicalDeviceFeatures2*) node;
                break;
            }
            curr = node->pNext;
        }
        if (chainedFeatures != NULL && chainedFeatures->features.fillModeNonSolid == VK_TRUE) {
            chainedOriginalFillMode = VK_TRUE;
            chainedFeatures->features.fillModeNonSolid = VK_FALSE;
        }
        if (pCreateInfo->pEnabledFeatures != NULL && pCreateInfo->pEnabledFeatures->fillModeNonSolid == VK_TRUE &&
            pCreateInfo->pEnabledFeatures != (chainedFeatures != NULL ? &chainedFeatures->features : NULL)) {
            patchedFeatures = *pCreateInfo->pEnabledFeatures;
            patchedFeatures.fillModeNonSolid = VK_FALSE;
            featuresCopied = true;
        }
    }

    VkResult result;
    if (mode.translate && extEnabled) {
        const char** names = malloc((pCreateInfo->enabledExtensionCount + 1) * sizeof(char*));
        if (names == NULL) return VK_ERROR_OUT_OF_HOST_MEMORY;
        uint32_t count = 0;
        for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount; i++) {
            const char* name = pCreateInfo->ppEnabledExtensionNames[i];
            if (strcmp(name, VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME) != 0) names[count++] = name;
        }
        if (mode.add_khr && !khrEnabled) names[count++] = VK_KHR_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME;

        VkDeviceCreateInfo patched = *pCreateInfo;
        if (featuresCopied) patched.pEnabledFeatures = &patchedFeatures;
        patched.ppEnabledExtensionNames = names;
        patched.enabledExtensionCount = count;

        // EXT v1/v2 语义下 divisor 无需显式启用，而 KHR/核心要求 features 结构置位。
        // 两个特性位以驱动报告值为准：应用未提供节点时按查询结果补写，
        // 自带节点时把驱动不支持的位临时置回 VK_FALSE，调用后恢复
        VkPhysicalDeviceVertexAttributeDivisorFeaturesKHR injected = {
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_KHR,
                NULL,
                mode.divisor_feature ? VK_TRUE : VK_FALSE,
                mode.zero_divisor_feature ? VK_TRUE : VK_FALSE,
        };
        VkPhysicalDeviceVertexAttributeDivisorFeaturesKHR* appFeatures =
                (VkPhysicalDeviceVertexAttributeDivisorFeaturesKHR*)
                        chain_find(patched.pNext, VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_KHR);
        VkBool32 appDivisorOriginal = VK_FALSE;
        VkBool32 appZeroDivisorOriginal = VK_FALSE;
        void** restore = NULL;
        if (appFeatures != NULL) {
            if (!mode.divisor_feature && appFeatures->vertexAttributeInstanceRateDivisor == VK_TRUE) {
                appDivisorOriginal = VK_TRUE;
                appFeatures->vertexAttributeInstanceRateDivisor = VK_FALSE;
            }
            if (!mode.zero_divisor_feature && appFeatures->vertexAttributeInstanceRateZeroDivisor == VK_TRUE) {
                appZeroDivisorOriginal = VK_TRUE;
                appFeatures->vertexAttributeInstanceRateZeroDivisor = VK_FALSE;
            }
        } else {
            restore = chain_splice_tail((void**) &patched.pNext, &injected);
        }

        result = real.create_device(physicalDevice, &patched, pAllocator, pDevice);
        if (restore != NULL) *restore = NULL;
        if (appDivisorOriginal) appFeatures->vertexAttributeInstanceRateDivisor = VK_TRUE;
        if (appZeroDivisorOriginal) appFeatures->vertexAttributeInstanceRateZeroDivisor = VK_TRUE;
        free(names);
    } else {
        VkDeviceCreateInfo patched = *pCreateInfo;
        if (featuresCopied) patched.pEnabledFeatures = &patchedFeatures;
        result = real.create_device(physicalDevice, &patched, pAllocator, pDevice);
    }

    // pNext 链内的 Features2 是应用内存，调用结束后恢复原值
    if (mode.fns_emulated && chainedFeatures != NULL && chainedOriginalFillMode == VK_TRUE &&
        pCreateInfo->pEnabledFeatures != &chainedFeatures->features) {
        chainedFeatures->features.fillModeNonSolid = VK_TRUE;
    }

    if (result == VK_SUCCESS && pDevice != NULL) {
        register_device_fns_emulation(*pDevice, mode.fns_emulated);
    }
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(const VkInstanceCreateInfo* pCreateInfo,
                                                const VkAllocationCallbacks* pAllocator, VkInstance* pInstance) {
    if (!g_ready) return VK_ERROR_INCOMPATIBLE_DRIVER;
    VkResult result = real_create_instance(pCreateInfo, pAllocator, pInstance);
    if (result == VK_SUCCESS && pInstance != NULL) ensure_instance_procs(*pInstance);
    // 自诊断：实例创建后立即经真实加载器枚举一次，区分"游戏进程内 ICD 加载失败"与
    // "应用的枚举调用未经过 shim"
    if (result == VK_SUCCESS && real.enumerate_physical_devices != NULL) {
        uint32_t count = 0;
        VkResult probe = real.enumerate_physical_devices(*pInstance, &count, NULL);
        FCL_LOG("vkshim: self-probe vkEnumeratePhysicalDevices=%d count=%u", probe, count);
        if (probe == VK_SUCCESS && count > 0 && real.get_physical_device_properties != NULL) {
            VkPhysicalDevice pd;
            count = 1;
            if (real.enumerate_physical_devices(*pInstance, &count, &pd) == VK_SUCCESS) {
                VkPhysicalDeviceProperties properties;
                real.get_physical_device_properties(pd, &properties);
                FCL_LOG("vkshim: driver %s (apiVersion=0x%x)", properties.deviceName,
                        properties.apiVersion);
            }
        }
    }
    return result;
}

VKAPI_ATTR void VKAPI_CALL vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks* pAllocator) {
    if (real_destroy_instance != NULL) real_destroy_instance(instance, pAllocator);
}

VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceExtensionProperties(const char* pLayerName, uint32_t* pPropertyCount,
                                                                      VkExtensionProperties* pProperties) {
    if (!g_ready) return VK_ERROR_INCOMPATIBLE_DRIVER;
    return real_enumerate_instance_extension_properties(pLayerName, pPropertyCount, pProperties);
}

VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceLayerProperties(uint32_t* pPropertyCount,
                                                                  VkLayerProperties* pProperties) {
    if (!g_ready) return VK_ERROR_INCOMPATIBLE_DRIVER;
    return real_enumerate_instance_layer_properties(pPropertyCount, pProperties);
}

VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceVersion(uint32_t* pApiVersion) {
    // 1.0 时代加载器未导出该函数，按其最高能力上报
    if (!g_ready || real_enumerate_instance_version == NULL) {
        if (pApiVersion != NULL) *pApiVersion = VK_API_VERSION_1_0;
        return VK_SUCCESS;
    }
    return real_enumerate_instance_version(pApiVersion);
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char* pName) {
    if (!g_ready || pName == NULL) return NULL;
    // 全局命令一并返回包装导出：应用常经 gipa(NULL, ...) 而非 dlsym 获取它们
    if (strcmp(pName, "vkGetInstanceProcAddr") == 0) return (PFN_vkVoidFunction) &vkGetInstanceProcAddr;
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) return (PFN_vkVoidFunction) &vkGetDeviceProcAddr;
    if (strcmp(pName, "vkCreateInstance") == 0) return (PFN_vkVoidFunction) &vkCreateInstance;
    if (strcmp(pName, "vkEnumerateInstanceExtensionProperties") == 0) {
        return (PFN_vkVoidFunction) &vkEnumerateInstanceExtensionProperties;
    }
    if (strcmp(pName, "vkEnumerateInstanceLayerProperties") == 0) {
        return (PFN_vkVoidFunction) &vkEnumerateInstanceLayerProperties;
    }
    if (strcmp(pName, "vkEnumerateInstanceVersion") == 0) return (PFN_vkVoidFunction) &vkEnumerateInstanceVersion;
    if (instance != NULL) {
        if (strcmp(pName, "vkEnumeratePhysicalDevices") == 0) {
            return (PFN_vkVoidFunction) &shim_enumerate_physical_devices;
        } else if (strcmp(pName, "vkEnumerateDeviceExtensionProperties") == 0) {
            return (PFN_vkVoidFunction) &shim_enumerate_device_extension_properties;
        } else if (strcmp(pName, "vkGetPhysicalDeviceFeatures") == 0) {
            return (PFN_vkVoidFunction) &shim_get_physical_device_features;
        } else if (strcmp(pName, "vkGetPhysicalDeviceFeatures2") == 0) {
            return (PFN_vkVoidFunction) &shim_get_physical_device_features2;
        } else if (strcmp(pName, "vkGetPhysicalDeviceFeatures2KHR") == 0) {
            return (PFN_vkVoidFunction) &shim_get_physical_device_features2_khr;
        } else if (strcmp(pName, "vkGetPhysicalDeviceProperties2") == 0) {
            return (PFN_vkVoidFunction) &shim_get_physical_device_properties2;
        } else if (strcmp(pName, "vkGetPhysicalDeviceProperties2KHR") == 0) {
            return (PFN_vkVoidFunction) &shim_get_physical_device_properties2_khr;
        } else if (strcmp(pName, "vkCreateDevice") == 0) {
            return (PFN_vkVoidFunction) &shim_create_device;
        } else if (strcmp(pName, "vkCreateGraphicsPipelines") == 0) {
            return (PFN_vkVoidFunction) &shim_create_graphics_pipelines;
        }
    }
    return real_get_instance_proc_addr(instance, pName);
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice device, const char* pName) {
    if (!g_ready || device == NULL || pName == NULL) return NULL;
    if (strcmp(pName, "vkCreateGraphicsPipelines") == 0) {
        return (PFN_vkVoidFunction) &shim_create_graphics_pipelines;
    } else if (strcmp(pName, "vkDestroyPipeline") == 0) {
        // 应用销毁 pipeline 时同步失效缓存条目，防止命中已销毁句柄导致画面卡死
        return (PFN_vkVoidFunction) &shim_destroy_pipeline;
    }
    return real_get_device_proc_addr(device, pName);
}

static VkResult shim_enumerate_physical_devices(VkInstance instance, uint32_t* pPhysicalDeviceCount,
                                                VkPhysicalDevice* pPhysicalDevices) {
    ensure_instance_procs(instance);
    if (real.enumerate_physical_devices == NULL) {
        FCL_LOG("vkshim: vkEnumeratePhysicalDevices dropped, real proc unresolved");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    VkResult result = real.enumerate_physical_devices(instance, pPhysicalDeviceCount, pPhysicalDevices);
    FCL_LOG("vkshim: vkEnumeratePhysicalDevices -> %d count=%u", result,
            (result == VK_SUCCESS || result == VK_INCOMPLETE) && pPhysicalDeviceCount != NULL
                    ? *pPhysicalDeviceCount : 0);
    if ((result == VK_SUCCESS || result == VK_INCOMPLETE) && pPhysicalDevices != NULL) {
        for (uint32_t i = 0; i < *pPhysicalDeviceCount; i++) {
            driver_mode_of(pPhysicalDevices[i]);
        }
    }
    return result;
}

static void shim_get_physical_device_properties2(VkPhysicalDevice physicalDevice,
                                                 VkPhysicalDeviceProperties2* pProperties) {
    if (real.get_physical_device_properties2 == NULL) return;
    translate_properties2(physicalDevice, pProperties, real.get_physical_device_properties2);
}

static void shim_get_physical_device_properties2_khr(VkPhysicalDevice physicalDevice,
                                                     VkPhysicalDeviceProperties2* pProperties) {
    if (real.get_physical_device_properties2_khr == NULL) return;
    translate_properties2(physicalDevice, pProperties,
                          (PFN_vkGetPhysicalDeviceProperties2) real.get_physical_device_properties2_khr);
}

static void shim_get_physical_device_features(VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures* pFeatures) {
    if (real.get_physical_device_features == NULL || pFeatures == NULL) return;
    real.get_physical_device_features(physicalDevice, pFeatures);
    if (driver_mode_of(physicalDevice).fns_emulated) {
        pFeatures->fillModeNonSolid = VK_TRUE;
    }
}

static void shim_get_physical_device_features2(VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures2* pFeatures) {
    if (real.get_physical_device_features2 == NULL || pFeatures == NULL) return;
    real.get_physical_device_features2(physicalDevice, pFeatures);
    if (driver_mode_of(physicalDevice).fns_emulated) {
        pFeatures->features.fillModeNonSolid = VK_TRUE;
    }
}

static void shim_get_physical_device_features2_khr(VkPhysicalDevice physicalDevice,
                                                   VkPhysicalDeviceFeatures2* pFeatures) {
    if (real.get_physical_device_features2 == NULL || pFeatures == NULL) return;
    real.get_physical_device_features2(physicalDevice, pFeatures);
    if (driver_mode_of(physicalDevice).fns_emulated) {
        pFeatures->features.fillModeNonSolid = VK_TRUE;
    }
}

// fillModeNonSolid 模拟的降级路径：线框/点填充管线统一改回 FILL，
// 牺牲线框视觉换取可运行性；只对模拟设备生效，原生支持的设备早已直通
static VkResult shim_create_graphics_pipelines(VkDevice device, VkPipelineCache pipelineCache, uint32_t createInfoCount,
                                               const VkGraphicsPipelineCreateInfo* pCreateInfos,
                                               const VkAllocationCallbacks* pAllocator, VkPipeline* pPipelines) {
    if (real.create_graphics_pipelines == NULL) return VK_ERROR_INITIALIZATION_FAILED;
    if (pCreateInfos == NULL || createInfoCount == 0) {
        return real.create_graphics_pipelines(device, pipelineCache, createInfoCount, pCreateInfos, pAllocator,
                                              pPipelines);
    }

    // 仅在存在非 FILL 光栅化状态时才复制改写；pNext 浅拷贝在调用期间有效
    VkGraphicsPipelineCreateInfo* patched = NULL;
    VkPipelineRasterizationStateCreateInfo** replaced = NULL;
    uint32_t replacedCount = 0;
    if (device_fns_emulated(device)) {
        for (uint32_t i = 0; i < createInfoCount; i++) {
            const VkPipelineRasterizationStateCreateInfo* raster = pCreateInfos[i].pRasterizationState;
            if (raster != NULL && raster->polygonMode != VK_POLYGON_MODE_FILL) replacedCount++;
        }
        if (replacedCount > 0) {
            patched = malloc(createInfoCount * sizeof(VkGraphicsPipelineCreateInfo));
            replaced = malloc(replacedCount * sizeof(VkPipelineRasterizationStateCreateInfo*));
            if (patched == NULL || replaced == NULL) {
                free(patched);
                free(replaced);
                return VK_ERROR_OUT_OF_HOST_MEMORY;
            }
            memcpy(patched, pCreateInfos, createInfoCount * sizeof(VkGraphicsPipelineCreateInfo));
            uint32_t idx = 0;
            for (uint32_t i = 0; i < createInfoCount; i++) {
                const VkPipelineRasterizationStateCreateInfo* raster = patched[i].pRasterizationState;
                if (raster != NULL && raster->polygonMode != VK_POLYGON_MODE_FILL) {
                    VkPipelineRasterizationStateCreateInfo* mod =
                            malloc(sizeof(VkPipelineRasterizationStateCreateInfo));
                    if (mod == NULL) continue;
                    *mod = *raster;
                    mod->polygonMode = VK_POLYGON_MODE_FILL;
                    replaced[idx++] = mod;
                    patched[i].pRasterizationState = mod;
                }
            }
            replacedCount = idx;
        }
    }
    const VkGraphicsPipelineCreateInfo* finalInfos = patched != NULL ? patched : pCreateInfos;

    // VerseX M1：pipeline 对象级缓存。相同语义（shader 模块句柄 + 完整状态哈希）的
    // 创建请求直接返回已编译 pipeline，跳过驱动侧整条 create 调用链（编译尖峰/主线程
    // 卡顿的主要来源）。命中判定 = 64 位语义哈希一致，MC 同一 program 的 shader 模块
    // 句柄稳定，不同 program 哈希必不同，不会跨程序误命中。
    if (vsx_cache_enabled) {
        VkResult hit = vsx_cache_lookup(device, finalInfos, createInfoCount, pPipelines);
        if (hit == VK_SUCCESS) {
            if (patched != NULL) {
                for (uint32_t i = 0; i < replacedCount; i++) free(replaced[i]);
                free(replaced);
                free(patched);
            }
            return VK_SUCCESS;
        }
    }

    VkResult result = real.create_graphics_pipelines(device, pipelineCache, createInfoCount, finalInfos, pAllocator,
                                                     pPipelines);
    if (result == VK_SUCCESS && vsx_cache_enabled && pPipelines != NULL) {
        vsx_cache_store(device, finalInfos, createInfoCount, pPipelines);
    }

    if (patched != NULL) {
        for (uint32_t i = 0; i < replacedCount; i++) free(replaced[i]);
        free(replaced);
        free(patched);
    }
    return result;
}

/* ==================== VerseX M1：pipeline 对象级缓存 ====================
 * 目标：跳过 MC 重复创建同语义 pipeline 的整条驱动调用链（主线程编译/绑定尖峰）。
 * 语义指纹 = 64 位 FNV-1a 哈希，覆盖 shader 模块句柄（program 标识）与全部
 * 固定功能状态（vertex input / input assembly / tesselation / viewport /
 * rasterization / multisample / depth-stencil / color blend / dynamic state /
 * layout / renderPass / subpass / flags）。MC 同一 program 的 shader 模块句柄
 * 稳定，不同 program 哈希必不同，不会跨程序误命中。
 * 表为固定容量 FIFO：满时从头覆盖最老条目。线程安全用互斥锁。
 */

#define VSX_CACHE_CAPACITY 2048

struct vsx_cache_entry {
    VkDevice device;
    uint64_t hash;
    uint32_t createInfoCount;
    VkGraphicsPipelineCreateInfo* createInfos; /* 深拷贝，含全部子结构 */
    VkPipeline* pipelines;
};

static struct vsx_cache_entry g_vsx_entries[VSX_CACHE_CAPACITY];
static int g_vsx_entry_count;
static int g_vsx_next_slot;
static pthread_mutex_t g_vsx_lock = PTHREAD_MUTEX_INITIALIZER;

static uint64_t vsx_hash_bytes(uint64_t h, const void* p, size_t n) {
    const uint8_t* b = (const uint8_t*) p;
    for (size_t i = 0; i < n; i++) {
        h ^= b[i];
        h *= 0x100000001b3ULL;
    }
    return h;
}

static uint64_t vsx_hash_state(const VkPipelineShaderStageCreateInfo* s) {
    uint64_t h = 0xcbf29ce484222325ULL;
    h = vsx_hash_bytes(h, &s->stage, sizeof(s->stage));
    h = vsx_hash_bytes(h, &s->module, sizeof(s->module));
    if (s->pName != NULL) h = vsx_hash_bytes(h, s->pName, strlen(s->pName));
    if (s->pSpecializationInfo != NULL) {
        const VkSpecializationInfo* sp = s->pSpecializationInfo;
        h = vsx_hash_bytes(h, &sp->mapEntryCount, sizeof(sp->mapEntryCount));
        for (uint32_t i = 0; i < sp->mapEntryCount && sp->pMapEntries != NULL; i++)
            h = vsx_hash_bytes(h, &sp->pMapEntries[i], sizeof(sp->pMapEntries[i]));
        h = vsx_hash_bytes(h, &sp->dataSize, sizeof(sp->dataSize));
        if (sp->dataSize > 0 && sp->pData != NULL) h = vsx_hash_bytes(h, sp->pData, sp->dataSize);
    }
    return h;
}

static uint64_t vsx_hash_create_info(const VkGraphicsPipelineCreateInfo* ci) {
    uint64_t h = 0xcbf29ce484222325ULL;
    // pNext 链：MC 26.x 区块/场景渲染走 VkPipelineRenderingCreateInfo（动态渲染），
    // 其 attachment 格式/viewMask 是 pipeline 语义的一部分；不含在内会让不同
    // 渲染目标的 pipeline 撞 hash → 误命中 → 画面渲染错误/卡死。逐节点识别哈希。
    {
        const VkBaseOutStructure* node = (const VkBaseOutStructure*) ci->pNext;
        while (node != NULL) {
            if (node->sType == VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO) {
                const VkPipelineRenderingCreateInfo* r = (const VkPipelineRenderingCreateInfo*) node;
                h = vsx_hash_bytes(h, &r->viewMask, sizeof(r->viewMask));
                h = vsx_hash_bytes(h, &r->colorAttachmentCount, sizeof(r->colorAttachmentCount));
                for (uint32_t i = 0; i < r->colorAttachmentCount && r->pColorAttachmentFormats != NULL; i++)
                    h = vsx_hash_bytes(h, &r->pColorAttachmentFormats[i], sizeof(r->pColorAttachmentFormats[i]));
                h = vsx_hash_bytes(h, &r->depthAttachmentFormat, sizeof(r->depthAttachmentFormat));
                h = vsx_hash_bytes(h, &r->stencilAttachmentFormat, sizeof(r->stencilAttachmentFormat));
            }
            node = node->pNext;
        }
    }
    h = vsx_hash_bytes(h, &ci->flags, sizeof(ci->flags));
    h = vsx_hash_bytes(h, &ci->stageCount, sizeof(ci->stageCount));
    for (uint32_t i = 0; i < ci->stageCount && ci->pStages != NULL; i++) {
        uint64_t s = vsx_hash_state(&ci->pStages[i]);
        h = vsx_hash_bytes(h, &s, sizeof(s));
    }
    if (ci->pVertexInputState != NULL) {
        const VkPipelineVertexInputStateCreateInfo* v = ci->pVertexInputState;
        h = vsx_hash_bytes(h, &v->vertexBindingDescriptionCount, sizeof(v->vertexBindingDescriptionCount));
        for (uint32_t i = 0; i < v->vertexBindingDescriptionCount && v->pVertexBindingDescriptions != NULL; i++)
            h = vsx_hash_bytes(h, &v->pVertexBindingDescriptions[i], sizeof(v->pVertexBindingDescriptions[i]));
        h = vsx_hash_bytes(h, &v->vertexAttributeDescriptionCount, sizeof(v->vertexAttributeDescriptionCount));
        for (uint32_t i = 0; i < v->vertexAttributeDescriptionCount && v->pVertexAttributeDescriptions != NULL; i++)
            h = vsx_hash_bytes(h, &v->pVertexAttributeDescriptions[i], sizeof(v->pVertexAttributeDescriptions[i]));
        // divisor state（pVertexInputState->pNext 的 VkPipelineVertexInputDivisorStateCreateInfoKHR，
        // sType 1000190001 与 EXT 同值）：区块/实体 instancing 的 vertex divisor 是语义的一部分，
        // 漏掉会让不同 divisor 配置撞 hash → 误命中 → instanced 渲染错乱/画面冻结
        {
            const VkBaseOutStructure* vn = (const VkBaseOutStructure*) v->pNext;
            while (vn != NULL) {
                if (vn->sType == VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_DIVISOR_STATE_CREATE_INFO_KHR) {
                    const VkPipelineVertexInputDivisorStateCreateInfoKHR* d =
                            (const VkPipelineVertexInputDivisorStateCreateInfoKHR*) vn;
                    h = vsx_hash_bytes(h, &d->vertexBindingDivisorCount, sizeof(d->vertexBindingDivisorCount));
                    for (uint32_t i = 0; i < d->vertexBindingDivisorCount && d->pVertexBindingDivisors != NULL; i++)
                        h = vsx_hash_bytes(h, &d->pVertexBindingDivisors[i],
                                           sizeof(d->pVertexBindingDivisors[i]));
                }
                vn = vn->pNext;
            }
        }
    }
    if (ci->pInputAssemblyState != NULL) {
        const VkPipelineInputAssemblyStateCreateInfo* a = ci->pInputAssemblyState;
        h = vsx_hash_bytes(h, &a->topology, sizeof(a->topology));
        h = vsx_hash_bytes(h, &a->primitiveRestartEnable, sizeof(a->primitiveRestartEnable));
    }
    if (ci->pTessellationState != NULL) {
        h = vsx_hash_bytes(h, &ci->pTessellationState->patchControlPoints,
                           sizeof(ci->pTessellationState->patchControlPoints));
    }
    if (ci->pViewportState != NULL) {
        const VkPipelineViewportStateCreateInfo* v = ci->pViewportState;
        h = vsx_hash_bytes(h, &v->viewportCount, sizeof(v->viewportCount));
        for (uint32_t i = 0; i < v->viewportCount && v->pViewports != NULL; i++)
            h = vsx_hash_bytes(h, &v->pViewports[i], sizeof(v->pViewports[i]));
        h = vsx_hash_bytes(h, &v->scissorCount, sizeof(v->scissorCount));
        for (uint32_t i = 0; i < v->scissorCount && v->pScissors != NULL; i++)
            h = vsx_hash_bytes(h, &v->pScissors[i], sizeof(v->pScissors[i]));
    }
    if (ci->pRasterizationState != NULL) {
        const VkPipelineRasterizationStateCreateInfo* r = ci->pRasterizationState;
        h = vsx_hash_bytes(h, &r->depthClampEnable, sizeof(r->depthClampEnable));
        h = vsx_hash_bytes(h, &r->rasterizerDiscardEnable, sizeof(r->rasterizerDiscardEnable));
        h = vsx_hash_bytes(h, &r->polygonMode, sizeof(r->polygonMode));
        h = vsx_hash_bytes(h, &r->cullMode, sizeof(r->cullMode));
        h = vsx_hash_bytes(h, &r->frontFace, sizeof(r->frontFace));
        h = vsx_hash_bytes(h, &r->depthBiasEnable, sizeof(r->depthBiasEnable));
        h = vsx_hash_bytes(h, &r->depthBiasConstantFactor, sizeof(r->depthBiasConstantFactor));
        h = vsx_hash_bytes(h, &r->depthBiasClamp, sizeof(r->depthBiasClamp));
        h = vsx_hash_bytes(h, &r->depthBiasSlopeFactor, sizeof(r->depthBiasSlopeFactor));
        h = vsx_hash_bytes(h, &r->lineWidth, sizeof(r->lineWidth));
    }
    if (ci->pMultisampleState != NULL) {
        const VkPipelineMultisampleStateCreateInfo* m = ci->pMultisampleState;
        h = vsx_hash_bytes(h, &m->rasterizationSamples, sizeof(m->rasterizationSamples));
        h = vsx_hash_bytes(h, &m->sampleShadingEnable, sizeof(m->sampleShadingEnable));
        h = vsx_hash_bytes(h, &m->minSampleShading, sizeof(m->minSampleShading));
        h = vsx_hash_bytes(h, &m->alphaToCoverageEnable, sizeof(m->alphaToCoverageEnable));
        h = vsx_hash_bytes(h, &m->alphaToOneEnable, sizeof(m->alphaToOneEnable));
    }
    if (ci->pDepthStencilState != NULL) {
        const VkPipelineDepthStencilStateCreateInfo* d = ci->pDepthStencilState;
        h = vsx_hash_bytes(h, &d->depthTestEnable, sizeof(d->depthTestEnable));
        h = vsx_hash_bytes(h, &d->depthWriteEnable, sizeof(d->depthWriteEnable));
        h = vsx_hash_bytes(h, &d->depthCompareOp, sizeof(d->depthCompareOp));
        h = vsx_hash_bytes(h, &d->depthBoundsTestEnable, sizeof(d->depthBoundsTestEnable));
        h = vsx_hash_bytes(h, &d->stencilTestEnable, sizeof(d->stencilTestEnable));
        h = vsx_hash_bytes(h, &d->front, sizeof(d->front));
        h = vsx_hash_bytes(h, &d->back, sizeof(d->back));
        h = vsx_hash_bytes(h, &d->minDepthBounds, sizeof(d->minDepthBounds));
        h = vsx_hash_bytes(h, &d->maxDepthBounds, sizeof(d->maxDepthBounds));
    }
    if (ci->pColorBlendState != NULL) {
        const VkPipelineColorBlendStateCreateInfo* c = ci->pColorBlendState;
        h = vsx_hash_bytes(h, &c->logicOpEnable, sizeof(c->logicOpEnable));
        h = vsx_hash_bytes(h, &c->logicOp, sizeof(c->logicOp));
        h = vsx_hash_bytes(h, &c->attachmentCount, sizeof(c->attachmentCount));
        for (uint32_t i = 0; i < c->attachmentCount; i++)
            h = vsx_hash_bytes(h, &c->pAttachments[i], sizeof(c->pAttachments[i]));
        h = vsx_hash_bytes(h, c->blendConstants, sizeof(c->blendConstants));
    }
    if (ci->pDynamicState != NULL) {
        const VkPipelineDynamicStateCreateInfo* d = ci->pDynamicState;
        h = vsx_hash_bytes(h, &d->dynamicStateCount, sizeof(d->dynamicStateCount));
        for (uint32_t i = 0; i < d->dynamicStateCount; i++)
            h = vsx_hash_bytes(h, &d->pDynamicStates[i], sizeof(d->pDynamicStates[i]));
    }
    h = vsx_hash_bytes(h, &ci->layout, sizeof(ci->layout));
    h = vsx_hash_bytes(h, &ci->renderPass, sizeof(ci->renderPass));
    h = vsx_hash_bytes(h, &ci->subpass, sizeof(ci->subpass));
    h = vsx_hash_bytes(h, &ci->basePipelineHandle, sizeof(ci->basePipelineHandle));
    h = vsx_hash_bytes(h, &ci->basePipelineIndex, sizeof(ci->basePipelineIndex));
    return h;
}

static uint64_t vsx_hash_infos(const VkGraphicsPipelineCreateInfo* in, uint32_t count) {
    uint64_t h = 0xcbf29ce484222325ULL;
    for (uint32_t i = 0; i < count; i++) {
        uint64_t s = vsx_hash_create_info(&in[i]);
        h = vsx_hash_bytes(h, &s, sizeof(s));
    }
    return h;
}

/* 在 pNext 链中查找指定的结构节点；条目克隆只保留语义相关节点（rendering/divisor），
 * 其余节点置 NULL，故查找可精确匹配克隆与原始链 */
static const VkPipelineRenderingCreateInfo* vsx_find_rendering(const void* pNext) {
    const VkBaseOutStructure* node = (const VkBaseOutStructure*) pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
            return (const VkPipelineRenderingCreateInfo*) node;
        node = node->pNext;
    }
    return NULL;
}

static const VkPipelineVertexInputDivisorStateCreateInfoKHR* vsx_find_divisor(const void* pNext) {
    const VkBaseOutStructure* node = (const VkBaseOutStructure*) pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_DIVISOR_STATE_CREATE_INFO_KHR)
            return (const VkPipelineVertexInputDivisorStateCreateInfoKHR*) node;
        node = node->pNext;
    }
    return NULL;
}

/* ---- 深拷贝 createInfo 数组（含全部子结构），缓存条目持有 ---- */
static VkGraphicsPipelineCreateInfo* vsx_clone_infos(const VkGraphicsPipelineCreateInfo* in, uint32_t count) {
    VkGraphicsPipelineCreateInfo* out = calloc(count, sizeof(VkGraphicsPipelineCreateInfo));
    if (out == NULL) return NULL;
    for (uint32_t i = 0; i < count; i++) {
        out[i] = in[i];
        out[i].pNext = NULL;
        /* 顶层 pNext：克隆 VkPipelineRenderingCreateInfo（动态渲染 attachment 格式），
         * 与哈希输入保持一致，供命中精确比对 */
        {
            const VkPipelineRenderingCreateInfo* r = vsx_find_rendering(in[i].pNext);
            if (r != NULL) {
                VkPipelineRenderingCreateInfo* rc = calloc(1, sizeof(*rc));
                if (rc == NULL) goto fail;
                *rc = *r; rc->pNext = NULL;
                if (r->colorAttachmentCount > 0 && r->pColorAttachmentFormats != NULL) {
                    rc->pColorAttachmentFormats = calloc(r->colorAttachmentCount,
                                                         sizeof(r->pColorAttachmentFormats[0]));
                    if (rc->pColorAttachmentFormats == NULL) goto fail;
                    memcpy((void*) rc->pColorAttachmentFormats, r->pColorAttachmentFormats,
                           r->colorAttachmentCount * sizeof(r->pColorAttachmentFormats[0]));
                }
                out[i].pNext = (const void*) rc;
            }
        }
        if (in[i].pStages != NULL && in[i].stageCount > 0) {
            VkPipelineShaderStageCreateInfo* st = calloc(in[i].stageCount, sizeof(VkPipelineShaderStageCreateInfo));
            if (st == NULL) goto fail;
            memcpy(st, in[i].pStages, in[i].stageCount * sizeof(VkPipelineShaderStageCreateInfo));
            for (uint32_t j = 0; j < in[i].stageCount; j++) {
                if (in[i].pStages[j].pName != NULL) {
                    st[j].pName = strdup(in[i].pStages[j].pName);
                    if (st[j].pName == NULL) goto fail;
                }
                if (in[i].pStages[j].pSpecializationInfo != NULL) {
                    const VkSpecializationInfo* sp = in[i].pStages[j].pSpecializationInfo;
                    VkSpecializationInfo* c = calloc(1, sizeof(*c));
                    if (c == NULL) goto fail;
                    *c = *sp;
                    if (sp->mapEntryCount > 0 && sp->pMapEntries != NULL) {
                        c->pMapEntries = calloc(sp->mapEntryCount, sizeof(sp->pMapEntries[0]));
                        if (c->pMapEntries == NULL) goto fail;
                        memcpy((void*) c->pMapEntries, sp->pMapEntries,
                               sp->mapEntryCount * sizeof(sp->pMapEntries[0]));
                    }
                    if (sp->dataSize > 0 && sp->pData != NULL) {
                        c->pData = malloc(sp->dataSize);
                        if (c->pData == NULL) goto fail;
                        memcpy((void*) c->pData, sp->pData, sp->dataSize);
                    }
                    st[j].pSpecializationInfo = c;
                }
            }
            out[i].pStages = st;
        }
        if (in[i].pVertexInputState != NULL) {
            const VkPipelineVertexInputStateCreateInfo* v = in[i].pVertexInputState;
            VkPipelineVertexInputStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *v; c->pNext = NULL;
            if (v->vertexBindingDescriptionCount > 0 && v->pVertexBindingDescriptions != NULL) {
                c->pVertexBindingDescriptions = calloc(v->vertexBindingDescriptionCount,
                                                       sizeof(v->pVertexBindingDescriptions[0]));
                if (c->pVertexBindingDescriptions == NULL) goto fail;
                memcpy((void*) c->pVertexBindingDescriptions, v->pVertexBindingDescriptions,
                       v->vertexBindingDescriptionCount * sizeof(v->pVertexBindingDescriptions[0]));
            }
            if (v->vertexAttributeDescriptionCount > 0 && v->pVertexAttributeDescriptions != NULL) {
                c->pVertexAttributeDescriptions = calloc(v->vertexAttributeDescriptionCount,
                                                         sizeof(v->pVertexAttributeDescriptions[0]));
                if (c->pVertexAttributeDescriptions == NULL) goto fail;
                memcpy((void*) c->pVertexAttributeDescriptions, v->pVertexAttributeDescriptions,
                       v->vertexAttributeDescriptionCount * sizeof(v->pVertexAttributeDescriptions[0]));
            }
            /* divisor state pNext：区块/实体 instancing 的语义一部分，与哈希输入保持一致 */
            {
                const VkPipelineVertexInputDivisorStateCreateInfoKHR* d = vsx_find_divisor(v->pNext);
                if (d != NULL) {
                    VkPipelineVertexInputDivisorStateCreateInfoKHR* dc = calloc(1, sizeof(*dc));
                    if (dc == NULL) goto fail;
                    *dc = *d; dc->pNext = NULL;
                    if (d->vertexBindingDivisorCount > 0 && d->pVertexBindingDivisors != NULL) {
                        dc->pVertexBindingDivisors = calloc(d->vertexBindingDivisorCount,
                                                            sizeof(d->pVertexBindingDivisors[0]));
                        if (dc->pVertexBindingDivisors == NULL) goto fail;
                        memcpy((void*) dc->pVertexBindingDivisors, d->pVertexBindingDivisors,
                               d->vertexBindingDivisorCount * sizeof(d->pVertexBindingDivisors[0]));
                    }
                    c->pNext = (const void*) dc;
                }
            }
            out[i].pVertexInputState = c;
        }
        if (in[i].pInputAssemblyState != NULL) {
            VkPipelineInputAssemblyStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *in[i].pInputAssemblyState; c->pNext = NULL;
            out[i].pInputAssemblyState = c;
        }
        if (in[i].pTessellationState != NULL) {
            VkPipelineTessellationStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *in[i].pTessellationState; c->pNext = NULL;
            out[i].pTessellationState = c;
        }
        if (in[i].pViewportState != NULL) {
            const VkPipelineViewportStateCreateInfo* v = in[i].pViewportState;
            VkPipelineViewportStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *v; c->pNext = NULL;
            if (v->viewportCount > 0 && v->pViewports != NULL) {
                c->pViewports = calloc(v->viewportCount, sizeof(v->pViewports[0]));
                if (c->pViewports == NULL) goto fail;
                memcpy((void*) c->pViewports, v->pViewports, v->viewportCount * sizeof(v->pViewports[0]));
            }
            if (v->scissorCount > 0 && v->pScissors != NULL) {
                c->pScissors = calloc(v->scissorCount, sizeof(v->pScissors[0]));
                if (c->pScissors == NULL) goto fail;
                memcpy((void*) c->pScissors, v->pScissors, v->scissorCount * sizeof(v->pScissors[0]));
            }
            out[i].pViewportState = c;
        }
        if (in[i].pRasterizationState != NULL) {
            VkPipelineRasterizationStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *in[i].pRasterizationState; c->pNext = NULL;
            out[i].pRasterizationState = c;
        }
        if (in[i].pMultisampleState != NULL) {
            VkPipelineMultisampleStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *in[i].pMultisampleState; c->pNext = NULL;
            if (in[i].pMultisampleState->pSampleMask != NULL && in[i].pMultisampleState->rasterizationSamples > 0) {
                uint32_t n = (in[i].pMultisampleState->rasterizationSamples + 31) / 32;
                c->pSampleMask = calloc(n, sizeof(VkSampleMask));
                if (c->pSampleMask == NULL) goto fail;
                memcpy((void*) c->pSampleMask, in[i].pMultisampleState->pSampleMask, n * sizeof(VkSampleMask));
            }
            out[i].pMultisampleState = c;
        }
        if (in[i].pDepthStencilState != NULL) {
            VkPipelineDepthStencilStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *in[i].pDepthStencilState; c->pNext = NULL;
            out[i].pDepthStencilState = c;
        }
        if (in[i].pColorBlendState != NULL) {
            const VkPipelineColorBlendStateCreateInfo* v = in[i].pColorBlendState;
            VkPipelineColorBlendStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *v; c->pNext = NULL;
            if (v->attachmentCount > 0 && v->pAttachments != NULL) {
                c->pAttachments = calloc(v->attachmentCount, sizeof(v->pAttachments[0]));
                if (c->pAttachments == NULL) goto fail;
                memcpy((void*) c->pAttachments, v->pAttachments, v->attachmentCount * sizeof(v->pAttachments[0]));
            }
            out[i].pColorBlendState = c;
        }
        if (in[i].pDynamicState != NULL) {
            const VkPipelineDynamicStateCreateInfo* v = in[i].pDynamicState;
            VkPipelineDynamicStateCreateInfo* c = calloc(1, sizeof(*c));
            if (c == NULL) goto fail;
            *c = *v; c->pNext = NULL;
            if (v->dynamicStateCount > 0 && v->pDynamicStates != NULL) {
                c->pDynamicStates = calloc(v->dynamicStateCount, sizeof(v->pDynamicStates[0]));
                if (c->pDynamicStates == NULL) goto fail;
                memcpy((void*) c->pDynamicStates, v->pDynamicStates, v->dynamicStateCount * sizeof(v->pDynamicStates[0]));
            }
            out[i].pDynamicState = c;
        }
    }
    return out;
fail:
    for (uint32_t i = 0; i < count; i++) {
        if (out[i].pStages != NULL) {
            for (uint32_t j = 0; j < out[i].stageCount; j++) {
                if (out[i].pStages[j].pSpecializationInfo != NULL) {
                    const VkSpecializationInfo* sp = out[i].pStages[j].pSpecializationInfo;
                    free((void*) sp->pMapEntries);
                    free((void*) sp->pData);
                    free((void*) sp);
                }
                free((void*) out[i].pStages[j].pName);
            }
            free((void*) out[i].pStages);
        }
        if (out[i].pVertexInputState != NULL) {
            const VkPipelineVertexInputDivisorStateCreateInfoKHR* d = vsx_find_divisor(out[i].pVertexInputState->pNext);
            if (d != NULL) {
                free((void*) d->pVertexBindingDivisors);
                free((void*) d);
            }
        }
        if (out[i].pNext != NULL) {
            const VkPipelineRenderingCreateInfo* r = vsx_find_rendering(out[i].pNext);
            if (r != NULL) {
                free((void*) r->pColorAttachmentFormats);
                free((void*) r);
            }
        }
        free((void*) out[i].pVertexInputState);
        free((void*) out[i].pInputAssemblyState);
        free((void*) out[i].pTessellationState);
        free((void*) out[i].pViewportState);
        free((void*) out[i].pRasterizationState);
        free((void*) out[i].pMultisampleState);
        free((void*) out[i].pDepthStencilState);
        free((void*) out[i].pColorBlendState);
        free((void*) out[i].pDynamicState);
    }
    free(out);
    return NULL;
}

/* ---- 命中精确比对：64 位 FNV-1a 哈希存在碰撞概率，命中前必须逐字段比对。
 * 缓存条目持有与哈希输入一致的完整深拷贝（含 specialization / divisor /
 * rendering pNext），任何差异一律视为未命中，交给真实驱动重新编译。 ---- */

static int vsx_spec_equal(const VkSpecializationInfo* a, const VkSpecializationInfo* b) {
    if (a == b) return 1;
    if (a == NULL || b == NULL) return 0;
    if (a->mapEntryCount != b->mapEntryCount || a->dataSize != b->dataSize) return 0;
    if (a->mapEntryCount > 0) {
        if (a->pMapEntries == NULL || b->pMapEntries == NULL) return 0;
        if (memcmp(a->pMapEntries, b->pMapEntries,
                   a->mapEntryCount * sizeof(a->pMapEntries[0])) != 0) return 0;
    }
    if (a->dataSize > 0) {
        if (a->pData == NULL || b->pData == NULL) return 0;
        if (memcmp(a->pData, b->pData, a->dataSize) != 0) return 0;
    }
    return 1;
}

static int vsx_stage_equal(const VkPipelineShaderStageCreateInfo* a,
                           const VkPipelineShaderStageCreateInfo* b) {
    if (a->flags != b->flags) return 0;
    if (a->stage != b->stage) return 0;
    if (a->module != b->module) return 0;
    if ((a->pName == NULL) != (b->pName == NULL)) return 0;
    if (a->pName != NULL && strcmp(a->pName, b->pName) != 0) return 0;
    return vsx_spec_equal(a->pSpecializationInfo, b->pSpecializationInfo);
}

static int vsx_vi_equal(const VkPipelineVertexInputStateCreateInfo* a,
                        const VkPipelineVertexInputStateCreateInfo* b) {
    if (a->flags != b->flags) return 0;
    if (a->vertexBindingDescriptionCount != b->vertexBindingDescriptionCount) return 0;
    if (a->vertexBindingDescriptionCount > 0 &&
        memcmp(a->pVertexBindingDescriptions, b->pVertexBindingDescriptions,
               a->vertexBindingDescriptionCount * sizeof(a->pVertexBindingDescriptions[0])) != 0) return 0;
    if (a->vertexAttributeDescriptionCount != b->vertexAttributeDescriptionCount) return 0;
    if (a->vertexAttributeDescriptionCount > 0 &&
        memcmp(a->pVertexAttributeDescriptions, b->pVertexAttributeDescriptions,
               a->vertexAttributeDescriptionCount * sizeof(a->pVertexAttributeDescriptions[0])) != 0) return 0;
    const VkPipelineVertexInputDivisorStateCreateInfoKHR* da = vsx_find_divisor(a->pNext);
    const VkPipelineVertexInputDivisorStateCreateInfoKHR* db = vsx_find_divisor(b->pNext);
    if ((da == NULL) != (db == NULL)) return 0;
    if (da != NULL) {
        if (da->vertexBindingDivisorCount != db->vertexBindingDivisorCount) return 0;
        if (da->vertexBindingDivisorCount > 0 &&
            memcmp(da->pVertexBindingDivisors, db->pVertexBindingDivisors,
                   da->vertexBindingDivisorCount * sizeof(da->pVertexBindingDivisors[0])) != 0) return 0;
    }
    return 1;
}

static int vsx_rendering_equal(const VkPipelineRenderingCreateInfo* a,
                               const VkPipelineRenderingCreateInfo* b) {
    if (a == b) return 1;
    if (a == NULL || b == NULL) return 0;
    if (a->viewMask != b->viewMask) return 0;
    if (a->colorAttachmentCount != b->colorAttachmentCount) return 0;
    if (a->colorAttachmentCount > 0 &&
        memcmp(a->pColorAttachmentFormats, b->pColorAttachmentFormats,
               a->colorAttachmentCount * sizeof(a->pColorAttachmentFormats[0])) != 0) return 0;
    if (a->depthAttachmentFormat != b->depthAttachmentFormat) return 0;
    if (a->stencilAttachmentFormat != b->stencilAttachmentFormat) return 0;
    return 1;
}

static int vsx_create_info_equal(const VkGraphicsPipelineCreateInfo* a,
                                 const VkGraphicsPipelineCreateInfo* b) {
    if (a->flags != b->flags) return 0;
    if (a->stageCount != b->stageCount) return 0;
    for (uint32_t i = 0; i < a->stageCount; i++) {
        if (a->pStages == NULL || b->pStages == NULL) return 0;
        if (!vsx_stage_equal(&a->pStages[i], &b->pStages[i])) return 0;
    }
    if ((a->pVertexInputState == NULL) != (b->pVertexInputState == NULL)) return 0;
    if (a->pVertexInputState != NULL && !vsx_vi_equal(a->pVertexInputState, b->pVertexInputState)) return 0;
    if ((a->pInputAssemblyState == NULL) != (b->pInputAssemblyState == NULL)) return 0;
    if (a->pInputAssemblyState != NULL &&
        (a->pInputAssemblyState->flags != b->pInputAssemblyState->flags ||
         a->pInputAssemblyState->topology != b->pInputAssemblyState->topology ||
         a->pInputAssemblyState->primitiveRestartEnable != b->pInputAssemblyState->primitiveRestartEnable)) return 0;
    if ((a->pTessellationState == NULL) != (b->pTessellationState == NULL)) return 0;
    if (a->pTessellationState != NULL &&
        (a->pTessellationState->flags != b->pTessellationState->flags ||
         a->pTessellationState->patchControlPoints != b->pTessellationState->patchControlPoints)) return 0;
    if ((a->pViewportState == NULL) != (b->pViewportState == NULL)) return 0;
    if (a->pViewportState != NULL) {
        const VkPipelineViewportStateCreateInfo* va = a->pViewportState;
        const VkPipelineViewportStateCreateInfo* vb = b->pViewportState;
        if (va->flags != vb->flags) return 0;
        if (va->viewportCount != vb->viewportCount) return 0;
        if (va->viewportCount > 0 &&
            memcmp(va->pViewports, vb->pViewports,
                   va->viewportCount * sizeof(va->pViewports[0])) != 0) return 0;
        if (va->scissorCount != vb->scissorCount) return 0;
        if (va->scissorCount > 0 &&
            memcmp(va->pScissors, vb->pScissors,
                   va->scissorCount * sizeof(va->pScissors[0])) != 0) return 0;
    }
    if ((a->pRasterizationState == NULL) != (b->pRasterizationState == NULL)) return 0;
    if (a->pRasterizationState != NULL) {
        const VkPipelineRasterizationStateCreateInfo* ra = a->pRasterizationState;
        const VkPipelineRasterizationStateCreateInfo* rb = b->pRasterizationState;
        if (ra->flags != rb->flags || ra->depthClampEnable != rb->depthClampEnable ||
            ra->rasterizerDiscardEnable != rb->rasterizerDiscardEnable ||
            ra->polygonMode != rb->polygonMode || ra->cullMode != rb->cullMode ||
            ra->frontFace != rb->frontFace || ra->depthBiasEnable != rb->depthBiasEnable ||
            ra->depthBiasConstantFactor != rb->depthBiasConstantFactor ||
            ra->depthBiasClamp != rb->depthBiasClamp ||
            ra->depthBiasSlopeFactor != rb->depthBiasSlopeFactor ||
            ra->lineWidth != rb->lineWidth) return 0;
    }
    if ((a->pMultisampleState == NULL) != (b->pMultisampleState == NULL)) return 0;
    if (a->pMultisampleState != NULL) {
        const VkPipelineMultisampleStateCreateInfo* ma = a->pMultisampleState;
        const VkPipelineMultisampleStateCreateInfo* mb = b->pMultisampleState;
        if (ma->flags != mb->flags || ma->rasterizationSamples != mb->rasterizationSamples ||
            ma->sampleShadingEnable != mb->sampleShadingEnable ||
            ma->minSampleShading != mb->minSampleShading ||
            ma->alphaToCoverageEnable != mb->alphaToCoverageEnable ||
            ma->alphaToOneEnable != mb->alphaToOneEnable) return 0;
        if ((ma->pSampleMask == NULL) != (mb->pSampleMask == NULL)) return 0;
        if (ma->pSampleMask != NULL) {
            uint32_t n = (ma->rasterizationSamples + 31) / 32;
            if (memcmp(ma->pSampleMask, mb->pSampleMask, n * sizeof(VkSampleMask)) != 0) return 0;
        }
    }
    if ((a->pDepthStencilState == NULL) != (b->pDepthStencilState == NULL)) return 0;
    if (a->pDepthStencilState != NULL &&
        (a->pDepthStencilState->flags != b->pDepthStencilState->flags ||
         a->pDepthStencilState->depthTestEnable != b->pDepthStencilState->depthTestEnable ||
         a->pDepthStencilState->depthWriteEnable != b->pDepthStencilState->depthWriteEnable ||
         a->pDepthStencilState->depthCompareOp != b->pDepthStencilState->depthCompareOp ||
         a->pDepthStencilState->depthBoundsTestEnable != b->pDepthStencilState->depthBoundsTestEnable ||
         a->pDepthStencilState->stencilTestEnable != b->pDepthStencilState->stencilTestEnable ||
         memcmp(&a->pDepthStencilState->front, &b->pDepthStencilState->front, sizeof(a->pDepthStencilState->front)) != 0 ||
         memcmp(&a->pDepthStencilState->back, &b->pDepthStencilState->back, sizeof(a->pDepthStencilState->back)) != 0 ||
         a->pDepthStencilState->minDepthBounds != b->pDepthStencilState->minDepthBounds ||
         a->pDepthStencilState->maxDepthBounds != b->pDepthStencilState->maxDepthBounds)) return 0;
    if ((a->pColorBlendState == NULL) != (b->pColorBlendState == NULL)) return 0;
    if (a->pColorBlendState != NULL) {
        const VkPipelineColorBlendStateCreateInfo* ca = a->pColorBlendState;
        const VkPipelineColorBlendStateCreateInfo* cb = b->pColorBlendState;
        if (ca->flags != cb->flags || ca->logicOpEnable != cb->logicOpEnable ||
            ca->logicOp != cb->logicOp || ca->attachmentCount != cb->attachmentCount) return 0;
        if (ca->attachmentCount > 0 &&
            memcmp(ca->pAttachments, cb->pAttachments,
                   ca->attachmentCount * sizeof(ca->pAttachments[0])) != 0) return 0;
        if (memcmp(ca->blendConstants, cb->blendConstants, sizeof(ca->blendConstants)) != 0) return 0;
    }
    if ((a->pDynamicState == NULL) != (b->pDynamicState == NULL)) return 0;
    if (a->pDynamicState != NULL) {
        const VkPipelineDynamicStateCreateInfo* da = a->pDynamicState;
        const VkPipelineDynamicStateCreateInfo* db = b->pDynamicState;
        if (da->flags != db->flags || da->dynamicStateCount != db->dynamicStateCount) return 0;
        if (da->dynamicStateCount > 0 &&
            memcmp(da->pDynamicStates, db->pDynamicStates,
                   da->dynamicStateCount * sizeof(da->pDynamicStates[0])) != 0) return 0;
    }
    if (a->layout != b->layout || a->renderPass != b->renderPass || a->subpass != b->subpass) return 0;
    if (a->basePipelineHandle != b->basePipelineHandle ||
        a->basePipelineIndex != b->basePipelineIndex) return 0;
    return vsx_rendering_equal(vsx_find_rendering(a->pNext), vsx_find_rendering(b->pNext));
}

static int vsx_infos_equal(const VkGraphicsPipelineCreateInfo* a, uint32_t countA,
                           const VkGraphicsPipelineCreateInfo* b, uint32_t countB) {
    if (countA != countB) return 0;
    for (uint32_t i = 0; i < countA; i++) {
        if (!vsx_create_info_equal(&a[i], &b[i])) return 0;
    }
    return 1;
}

static void vsx_entry_destroy(struct vsx_cache_entry* e) {
    if (e == NULL) return;
    if (e->createInfos != NULL) vsx_entry_destroy_count(e->createInfos, e->createInfoCount);
    free(e->createInfos);
    free(e->pipelines);
    memset(e, 0, sizeof(*e));
}

static VkResult vsx_cache_lookup(VkDevice device, const VkGraphicsPipelineCreateInfo* pCreateInfos,
                                 uint32_t createInfoCount, VkPipeline* pPipelines) {
    if (pCreateInfos == NULL || pPipelines == NULL || createInfoCount == 0) return VK_ERROR_INITIALIZATION_FAILED;
    uint64_t hash = vsx_hash_infos(pCreateInfos, createInfoCount);
    pthread_mutex_lock(&g_vsx_lock);
    for (int i = 0; i < g_vsx_entry_count; i++) {
        struct vsx_cache_entry* e = &g_vsx_entries[i];
        if (e->device == device && e->hash == hash && e->createInfoCount == createInfoCount &&
            vsx_infos_equal(e->createInfos, e->createInfoCount, pCreateInfos, createInfoCount)) {
            for (uint32_t j = 0; j < createInfoCount; j++) pPipelines[j] = e->pipelines[j];
            FCL_LOG("vsx: pipeline cache hit (hash=%016llx, count=%u)", (unsigned long long) hash, createInfoCount);
            pthread_mutex_unlock(&g_vsx_lock);
            return VK_SUCCESS;
        }
    }
    pthread_mutex_unlock(&g_vsx_lock);
    return VK_ERROR_INITIALIZATION_FAILED;
}

static void vsx_cache_store(VkDevice device, const VkGraphicsPipelineCreateInfo* pCreateInfos,
                            uint32_t createInfoCount, const VkPipeline* pPipelines) {
    if (pCreateInfos == NULL || pPipelines == NULL || createInfoCount == 0) return;
    uint64_t hash = vsx_hash_infos(pCreateInfos, createInfoCount);
    VkGraphicsPipelineCreateInfo* infos = vsx_clone_infos(pCreateInfos, createInfoCount);
    if (infos == NULL) return;
    VkPipeline* pipes = calloc(createInfoCount, sizeof(VkPipeline));
    if (pipes == NULL) {
        vsx_entry_destroy_count(infos, createInfoCount);
        free(infos);
        return;
    }
    memcpy(pipes, pPipelines, createInfoCount * sizeof(VkPipeline));

    pthread_mutex_lock(&g_vsx_lock);
    if (g_vsx_entry_count < VSX_CACHE_CAPACITY) {
        struct vsx_cache_entry* e = &g_vsx_entries[g_vsx_entry_count];
        e->device = device;
        e->hash = hash;
        e->createInfoCount = createInfoCount;
        e->createInfos = infos;
        e->pipelines = pipes;
        g_vsx_entry_count++;
        FCL_LOG("vsx: pipeline cache store (hash=%016llx, count=%u, total=%d)", (unsigned long long) hash,
                createInfoCount, g_vsx_entry_count);
    } else {
        // FIFO：覆盖最老条目（next_slot 循环推进）
        struct vsx_cache_entry* e = &g_vsx_entries[g_vsx_next_slot];
        vsx_entry_destroy(e);
        e->device = device;
        e->hash = hash;
        e->createInfoCount = createInfoCount;
        e->createInfos = infos;
        e->pipelines = pipes;
        g_vsx_next_slot = (g_vsx_next_slot + 1) % VSX_CACHE_CAPACITY;
        FCL_LOG("vsx: pipeline cache evict+store (hash=%016llx, count=%u)", (unsigned long long) hash,
                createInfoCount);
    }
    pthread_mutex_unlock(&g_vsx_lock);
}

static void vsx_entry_destroy_count(VkGraphicsPipelineCreateInfo* infos, uint32_t count) {
    for (uint32_t i = 0; i < count; i++) {
        VkGraphicsPipelineCreateInfo* ci = &infos[i];
        if (ci->pStages != NULL) {
            for (uint32_t j = 0; j < ci->stageCount; j++) {
                if (ci->pStages[j].pSpecializationInfo != NULL) {
                    const VkSpecializationInfo* sp = ci->pStages[j].pSpecializationInfo;
                    free((void*) sp->pMapEntries);
                    free((void*) sp->pData);
                    free((void*) sp);
                }
                free((void*) ci->pStages[j].pName);
            }
            free((void*) ci->pStages);
        }
        if (ci->pVertexInputState != NULL) {
            const VkPipelineVertexInputDivisorStateCreateInfoKHR* d = vsx_find_divisor(ci->pVertexInputState->pNext);
            if (d != NULL) {
                free((void*) d->pVertexBindingDivisors);
                free((void*) d);
            }
        }
        if (ci->pNext != NULL) {
            const VkPipelineRenderingCreateInfo* r = vsx_find_rendering(ci->pNext);
            if (r != NULL) {
                free((void*) r->pColorAttachmentFormats);
                free((void*) r);
            }
        }
        free((void*) ci->pVertexInputState);
        free((void*) ci->pInputAssemblyState);
        free((void*) ci->pTessellationState);
        free((void*) ci->pViewportState);
        free((void*) ci->pRasterizationState);
        free((void*) ci->pMultisampleState);
        free((void*) ci->pDepthStencilState);
        free((void*) ci->pColorBlendState);
        free((void*) ci->pDynamicState);
    }
}

/* 在 vkshim 构造器之后初始化：默认开启，环境变量 VERSERX_PIPELINE_CACHE=0 关闭 */
static void vsx_cache_init(void) {
    const char* v = getenv("VERSERX_PIPELINE_CACHE");
    vsx_cache_enabled = (v == NULL || v[0] == '\0' || strcmp(v, "0") != 0);
    FCL_LOG("vsx: pipeline cache %s", vsx_cache_enabled ? "enabled" : "disabled");
}

/* 应用销毁 pipeline 时从缓存表移除对应条目，防止后续命中返回已销毁句柄。
 * 移除采用"末位搬移"保持数组紧凑；条目含多个 pipeline 时任一被销毁即整条失效
 * （创建批次内各 pipeline 同生命周期）。 */
static void vsx_cache_invalidate_pipeline(VkDevice device, VkPipeline pipeline) {
    pthread_mutex_lock(&g_vsx_lock);
    for (int i = 0; i < g_vsx_entry_count; i++) {
        struct vsx_cache_entry* e = &g_vsx_entries[i];
        if (e->device != device) continue;
        for (uint32_t j = 0; j < e->createInfoCount; j++) {
            if (e->pipelines[j] == pipeline) {
                vsx_entry_destroy(e);
                if (i < g_vsx_entry_count - 1) g_vsx_entries[i] = g_vsx_entries[g_vsx_entry_count - 1];
                g_vsx_entry_count--;
                FCL_LOG("vsx: pipeline cache invalidated (pipeline=%p, total=%d)", (void*) pipeline,
                        g_vsx_entry_count);
                pthread_mutex_unlock(&g_vsx_lock);
                return;
            }
        }
    }
    pthread_mutex_unlock(&g_vsx_lock);
}

static PFN_vkDestroyPipeline real_destroy_pipeline;

static VkResult shim_destroy_pipeline(VkDevice device, VkPipeline pipeline, const VkAllocationCallbacks* pAllocator) {
    if (vsx_cache_enabled) vsx_cache_invalidate_pipeline(device, pipeline);
    if (real_destroy_pipeline == NULL) {
        real_destroy_pipeline = (PFN_vkDestroyPipeline) real_get_device_proc_addr(device, "vkDestroyPipeline");
    }
    if (real_destroy_pipeline != NULL) real_destroy_pipeline(device, pipeline, pAllocator);
    return VK_SUCCESS;
}
