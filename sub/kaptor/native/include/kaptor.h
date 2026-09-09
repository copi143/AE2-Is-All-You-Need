#ifndef KAPTOR_H
#define KAPTOR_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define KAPTOR_ABI_VERSION 3

#define KAPTOR_OK               0
#define KAPTOR_ERR_ARGS        -1
#define KAPTOR_ERR_NOT_INIT    -2
#define KAPTOR_ERR_NOT_FOUND   -3
#define KAPTOR_ERR_COMPILE     -5
#define KAPTOR_ERR_INSTANTIATE -6
#define KAPTOR_ERR_TRAP        -7
#define KAPTOR_ERR_BUFFER      -8
#define KAPTOR_ERR_QUEUE_FULL  -9
#define KAPTOR_ERR_TIMEOUT     -10
#define KAPTOR_ERR_ALREADY     -11
#define KAPTOR_ERR_IMPORT      -12

#define KAPTOR_FLAG_CANCELLED  1u
#define KAPTOR_CFG_NO_EPOCH    1u

#define KAPTOR_ARENA_MAGIC     0x4B505452u
#define KAPTOR_SLOT_EMPTY      0u
#define KAPTOR_SLOT_PENDING    1u
#define KAPTOR_SLOT_RUNNING    2u
#define KAPTOR_SLOT_DONE       3u
#define KAPTOR_SLOT_ERROR      4u

#define KAPTOR_PHASE_ALL       0u
#define KAPTOR_PHASE_BEFORE    1u
#define KAPTOR_PHASE_ON        2u
#define KAPTOR_PHASE_AFTER     3u

#define KAPTOR_TYPE_MAX        256u
#define KAPTOR_DATA_MAX        4096u
#define KAPTOR_RESULT_MAX      4096u

typedef struct KaptorEngine KaptorEngine;

typedef struct KaptorConfig {
    uint32_t struct_size;
    uint32_t flags;
    uint64_t fuel_default;
    uint32_t epoch_ms;
    uint32_t epoch_ticks;
    uint32_t memory_max;
    uint32_t reserved;
    uint32_t type_cap;
    uint32_t payload_cap;
    uint32_t result_cap;
} KaptorConfig;

typedef struct KaptorStatus {
    int32_t  code;
    uint32_t flags;
    uint32_t invoked;
    uint32_t error_count;
    uint64_t fuel_used;
} KaptorStatus;

typedef struct KaptorStats {
    uint64_t invokes;
    uint64_t traps;
    uint64_t fuel_used;
    uint64_t modules;
    uint64_t hooks;
} KaptorStats;

typedef struct KaptorModuleInfo {
    int32_t id;
    char    name[64];
} KaptorModuleInfo;

typedef struct KaptorHookInfo {
    int32_t id;
    int32_t module_id;
    char    phase[16];
    char    event[128];
    char    export_name[64];
} KaptorHookInfo;

typedef struct KaptorLayout {
    uint32_t struct_size;
    uint32_t header_size;
    uint32_t slot_hdr_size;
    uint32_t slot_size;
    uint32_t slot_count;
    uint32_t type_off;
    uint32_t payload_off;
    uint32_t result_off;
    uint32_t type_cap;
    uint32_t payload_cap;
    uint32_t result_cap;
} KaptorLayout;

int32_t kaptor_abi_version(void);

KaptorEngine *kaptor_engine_new(const KaptorConfig *cfg);
void          kaptor_engine_free(KaptorEngine *engine);

int32_t kaptor_engine_load(KaptorEngine *engine, const char *name,
                           const uint8_t *bytes, size_t len);
int32_t kaptor_engine_unload(KaptorEngine *engine, int32_t module_id);
int32_t kaptor_engine_reload(KaptorEngine *engine, int32_t module_id,
                             const uint8_t *bytes, size_t len);
int32_t kaptor_engine_register_hook(KaptorEngine *engine, int32_t module_id,
                                    const char *phase, const char *event,
                                    const char *export_name);
int32_t kaptor_engine_unregister_hook(KaptorEngine *engine, int32_t hook_id);
int32_t kaptor_engine_discover_hooks(KaptorEngine *engine, int32_t module_id);
int32_t kaptor_engine_set_fuel(KaptorEngine *engine, int32_t module_id, uint64_t fuel);
int32_t kaptor_engine_was_cancelled(KaptorEngine *engine);
int32_t kaptor_engine_last_error(KaptorEngine *engine, uint8_t *buf, size_t cap);
int32_t kaptor_engine_dump_registry(KaptorEngine *engine, uint8_t *buf, size_t cap);
int32_t kaptor_engine_list_modules(KaptorEngine *engine, int32_t *out_ids, size_t cap);
int32_t kaptor_engine_list_hooks(KaptorEngine *engine, int32_t *out_ids, size_t cap);
int32_t kaptor_engine_module_info(KaptorEngine *engine, int32_t module_id,
                                  KaptorModuleInfo *out);
int32_t kaptor_engine_hook_info(KaptorEngine *engine, int32_t hook_id,
                                KaptorHookInfo *out);
int32_t kaptor_engine_stats(KaptorEngine *engine, KaptorStats *out);
int32_t kaptor_engine_drain_logs(KaptorEngine *engine, uint8_t *buf, size_t cap);

int32_t kaptor_arena_init(uint64_t addr, size_t size,
                          uint32_t type_cap, uint32_t payload_cap, uint32_t result_cap);
int32_t kaptor_arena_layout(uint64_t addr, size_t size, KaptorLayout *out);
int32_t kaptor_slot_claim(uint64_t addr, size_t size);
int32_t kaptor_slot_write(uint64_t addr, size_t size, int32_t slot, uint64_t event_id, uint32_t phase,
                          const char *event, size_t event_len,
                          const uint8_t *payload, size_t payload_len);
int32_t kaptor_slot_result(uint64_t addr, size_t size, int32_t slot,
                           uint8_t *out, size_t out_cap, uint32_t *flags_out);
int32_t kaptor_slot_reset(uint64_t addr, size_t size, int32_t slot);

int32_t kaptor_engine_attach_arena(KaptorEngine *engine, uint64_t addr, size_t size);
int32_t kaptor_engine_layout(KaptorEngine *engine, KaptorLayout *out);
int32_t kaptor_engine_kick(KaptorEngine *engine, int32_t slot);
int32_t kaptor_engine_kick_pending(KaptorEngine *engine);

int32_t kaptor_engine_dispatch(KaptorEngine *engine,
                               const char *event, size_t event_len,
                               const uint8_t *payload, size_t payload_len,
                               uint8_t *out, size_t out_cap,
                               KaptorStatus *status);
int32_t kaptor_engine_dispatch_phase(KaptorEngine *engine,
                                     const char *event, size_t event_len,
                                     const char *phase,
                                     const uint8_t *payload, size_t payload_len,
                                     uint8_t *out, size_t out_cap,
                                     KaptorStatus *status);

int32_t kaptor_init(uint64_t buf_addr, size_t buf_size);
int32_t kaptor_shutdown(void);
int32_t kaptor_load_module(const char *name, const uint8_t *bytes, size_t len);
int32_t kaptor_unload_module(int32_t module_id);
int32_t kaptor_reload_module(int32_t module_id, const uint8_t *bytes, size_t len);
int32_t kaptor_register_hook(int32_t module_id, const char *hook_type,
                             const char *event_type, const char *export_name);
int32_t kaptor_unregister_hook(int32_t hook_id);
int32_t kaptor_discover_hooks(int32_t module_id);
int32_t kaptor_set_fuel(int32_t module_id, uint64_t fuel);
int32_t kaptor_was_cancelled(void);
int32_t kaptor_last_error(uint8_t *buf, size_t buf_size);
int32_t kaptor_dump_registry(uint8_t *buf, size_t buf_size);
int32_t kaptor_list_modules(int32_t *out_ids, size_t cap);
int32_t kaptor_dispatch_sync(const char *event_type, const char *json_data,
                             uint8_t *result_buf, size_t result_buf_size);
int32_t kaptor_dispatch_phase(const char *event_type, const char *phase,
                              const char *json_data, uint8_t *result_buf,
                              size_t result_buf_size);
int32_t kaptor_submit_event(const char *event_type, const char *json_data,
                            uint64_t event_id);
int32_t kaptor_wait_result(uint64_t event_id, uint32_t timeout_ms);
int32_t kaptor_read_result(int32_t slot_index, uint8_t *buf, size_t buf_size);
size_t  kaptor_slot_size(void);
size_t  kaptor_header_size(void);
uint32_t kaptor_magic_done(void);
uint32_t kaptor_magic_error(void);
uint32_t kaptor_magic_pending(void);
uint32_t kaptor_magic_empty(void);

#ifdef __cplusplus
}
#endif

#endif
