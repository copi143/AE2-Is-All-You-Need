mod abi;
mod arena;
mod config;
mod dispatch;
mod hooks;
mod host;
mod runtime;

use std::ffi::CStr;
use std::os::raw::c_char;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex, MutexGuard};
use std::thread;
use std::time::Duration;

use abi::{
    ABI_VERSION, ERR_ALREADY, ERR_ARGS, ERR_BUFFER, ERR_COMPILE, ERR_IMPORT, ERR_INSTANTIATE,
    ERR_NOT_FOUND, ERR_NOT_INIT, ERR_QUEUE_FULL, ERR_TIMEOUT, ERR_TRAP, OK,
};
use arena::{
    Arena, Layout, ARENA_HEADER_SIZE, PHASE_ALL, SLOT_DONE, SLOT_EMPTY, SLOT_ERROR, SLOT_PENDING,
};
use config::{
    EngineConfig, KaptorConfig, KaptorHookInfo, KaptorLayout, KaptorModuleInfo, KaptorStats,
    KaptorStatus,
};
use runtime::KaptorRuntime;

struct Wake {
    mu: Mutex<bool>,
    cvar: Condvar,
}

impl Wake {
    fn new() -> Arc<Self> {
        Arc::new(Wake {
            mu: Mutex::new(false),
            cvar: Condvar::new(),
        })
    }

    fn notify(&self) {
        let mut g = lock(&self.mu);
        *g = true;
        self.cvar.notify_one();
    }

    fn wait(&self, running: &AtomicBool) {
        let mut g = lock(&self.mu);
        while !*g && running.load(Ordering::Relaxed) {
            let (gg, _) = self
                .cvar
                .wait_timeout(g, Duration::from_millis(2))
                .unwrap_or_else(|e| e.into_inner());
            g = gg;
        }
        *g = false;
    }
}

pub struct KaptorEngine {
    rt: Arc<KaptorRuntime>,
    running: Arc<AtomicBool>,
    epoch: Mutex<Option<thread::JoinHandle<()>>>,
    arena: Mutex<Option<Arena>>,
    wake: Arc<Wake>,
}

struct EnginePtr(*mut KaptorEngine);
unsafe impl Send for EnginePtr {}

static GLOBAL: Mutex<Option<EnginePtr>> = Mutex::new(None);
static WORKER: Mutex<Option<thread::JoinHandle<()>>> = Mutex::new(None);
static BUFFER_ADDR: AtomicU64 = AtomicU64::new(0);
static BUFFER_SIZE: AtomicUsize = AtomicUsize::new(0);

unsafe fn cstr<'a>(p: *const c_char) -> &'a str {
    if p.is_null() {
        return "";
    }
    CStr::from_ptr(p).to_str().unwrap_or("")
}

unsafe fn bytes<'a>(p: *const u8, len: usize) -> &'a [u8] {
    if p.is_null() || len == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(p, len)
    }
}

fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

fn ffi(f: impl FnOnce() -> i32) -> i32 {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or(ERR_TRAP)
}

fn write_out(data: &[u8], buf: *mut u8, cap: usize) -> i32 {
    if buf.is_null() || cap == 0 {
        return ERR_ARGS;
    }
    if data.len() > cap {
        return ERR_BUFFER;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(data.as_ptr(), buf, data.len());
        if data.len() < cap {
            buf.add(data.len()).write(0u8);
        }
    }
    data.len() as i32
}

fn map_load_err(msg: &str) -> i32 {
    if msg.starts_with("compile") {
        ERR_COMPILE
    } else if msg.starts_with("forbidden import") {
        ERR_IMPORT
    } else if msg.starts_with("instantiate") || msg.contains("memory") {
        ERR_INSTANTIATE
    } else if msg.contains("trap") {
        ERR_TRAP
    } else if msg.contains("not found") {
        ERR_NOT_FOUND
    } else {
        ERR_ARGS
    }
}

fn buffer() -> Option<Arena> {
    let addr = BUFFER_ADDR.load(Ordering::SeqCst);
    let size = BUFFER_SIZE.load(Ordering::SeqCst);
    if addr == 0 {
        return None;
    }
    Arena::attach(addr, size).ok()
}

fn epoch_loop(engine: wasmtime::Engine, running: Arc<AtomicBool>, interval_ms: u64) {
    while running.load(Ordering::SeqCst) {
        thread::sleep(Duration::from_millis(interval_ms));
        engine.increment_epoch();
    }
}

fn worker_loop(rt: Arc<KaptorRuntime>, running: Arc<AtomicBool>, wake: Arc<Wake>) {
    while running.load(Ordering::SeqCst) {
        let rb = match buffer() {
            Some(b) => b,
            None => {
                wake.wait(&running);
                continue;
            }
        };
        let pending = rb.pending();
        if pending.is_empty() {
            wake.wait(&running);
            continue;
        }
        for idx in pending {
            if let Some(slot) = rb.slot(idx) {
                let _ = rt.process_slot(&slot);
            }
        }
    }
}

unsafe fn eng<'a>(engine: *mut KaptorEngine) -> Option<&'a KaptorEngine> {
    engine.as_ref()
}

fn fill_layout(layout: &Layout, out: *mut KaptorLayout) {
    if out.is_null() {
        return;
    }
    unsafe {
        *out = KaptorLayout {
            struct_size: std::mem::size_of::<KaptorLayout>() as u32,
            header_size: layout.header_size,
            slot_hdr_size: layout.slot_hdr_size,
            slot_size: layout.slot_size,
            slot_count: layout.slot_count,
            type_off: layout.type_off,
            payload_off: layout.payload_off,
            result_off: layout.result_off,
            type_cap: layout.type_cap,
            payload_cap: layout.payload_cap,
            result_cap: layout.result_cap,
        };
    }
}

fn fill_status(status: *mut KaptorStatus, result: &crate::dispatch::DispatchResult, code: i32) {
    if status.is_null() {
        return;
    }
    unsafe {
        *status = KaptorStatus::from_dispatch(
            code,
            result.invoked,
            result.errors.len() as u32,
            result.cancelled,
            result.fuel_used,
        );
    }
}

#[no_mangle]
pub extern "C" fn kaptor_abi_version() -> i32 {
    ABI_VERSION
}

#[no_mangle]
pub extern "C" fn kaptor_engine_new(cfg: *const KaptorConfig) -> *mut KaptorEngine {
    let rust_cfg = EngineConfig::from_c(unsafe { cfg.as_ref() });
    let rt = match KaptorRuntime::with_config(rust_cfg.clone()) {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };
    let running = Arc::new(AtomicBool::new(true));
    let epoch = if rust_cfg.start_epoch {
        let engine = rt.engine().clone();
        let flag = Arc::clone(&running);
        let ms = rust_cfg.epoch_interval_ms;
        Some(thread::spawn(move || epoch_loop(engine, flag, ms)))
    } else {
        None
    };
    Box::into_raw(Box::new(KaptorEngine {
        rt: Arc::new(rt),
        running,
        epoch: Mutex::new(epoch),
        arena: Mutex::new(None),
        wake: Wake::new(),
    }))
}

fn engine_arena(e: &KaptorEngine) -> Option<Arena> {
    *lock(&e.arena)
}

#[no_mangle]
pub extern "C" fn kaptor_engine_free(engine: *mut KaptorEngine) {
    if engine.is_null() {
        return;
    }
    let boxed = unsafe { Box::from_raw(engine) };
    boxed.running.store(false, Ordering::SeqCst);
    boxed.wake.notify();
    let handle = lock(&boxed.epoch).take();
    if let Some(h) = handle {
        let _ = h.join();
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_load(
    engine: *mut KaptorEngine,
    name: *const c_char,
    wasm: *const u8,
    len: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if wasm.is_null() || len == 0 {
        return ERR_ARGS;
    }
    match e
        .rt
        .load_module(unsafe { cstr(name) }, unsafe { bytes(wasm, len) })
    {
        Ok(id) => id,
        Err(err) => map_load_err(&err),
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_unload(engine: *mut KaptorEngine, module_id: i32) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    match e.rt.unload_module(module_id) {
        Ok(()) => OK,
        Err(_) => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_reload(
    engine: *mut KaptorEngine,
    module_id: i32,
    bytes_ptr: *const u8,
    len: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if bytes_ptr.is_null() || len == 0 {
        return ERR_ARGS;
    }
    match e.rt.reload_module(module_id, unsafe { bytes(bytes_ptr, len) }) {
        Ok(()) => OK,
        Err(err) => map_load_err(&err),
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_register_hook(
    engine: *mut KaptorEngine,
    module_id: i32,
    phase: *const c_char,
    event: *const c_char,
    export_name: *const c_char,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    match e.rt.register_hook(
        module_id,
        unsafe { cstr(phase) },
        unsafe { cstr(event) },
        unsafe { cstr(export_name) },
    ) {
        Ok(id) => id,
        Err(_) => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_unregister_hook(engine: *mut KaptorEngine, hook_id: i32) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    match e.rt.unregister_hook(hook_id) {
        Ok(()) => OK,
        Err(_) => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_discover_hooks(engine: *mut KaptorEngine, module_id: i32) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    match e.rt.discover_hooks(module_id) {
        Ok(ids) => ids.len() as i32,
        Err(_) => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_arena_init(
    addr: u64,
    size: usize,
    type_cap: u32,
    payload_cap: u32,
    result_cap: u32,
) -> i32 {
    match Arena::init(addr, size, type_cap, payload_cap, result_cap) {
        Ok(a) => a.layout.slot_count as i32,
        Err(_) => ERR_ARGS,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_arena_layout(addr: u64, size: usize, out: *mut KaptorLayout) -> i32 {
    if out.is_null() {
        return ERR_ARGS;
    }
    match Arena::attach(addr, size) {
        Ok(a) => {
            fill_layout(&a.layout, out);
            OK
        }
        Err(_) => ERR_ARGS,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_slot_claim(addr: u64, size: usize) -> i32 {
    match Arena::attach(addr, size) {
        Ok(a) => a.claim().map(|i| i as i32).unwrap_or(ERR_QUEUE_FULL),
        Err(_) => ERR_ARGS,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_slot_write(
    addr: u64,
    size: usize,
    slot: i32,
    event_id: u64,
    phase: u32,
    event: *const c_char,
    event_len: usize,
    payload: *const u8,
    payload_len: usize,
) -> i32 {
    let arena = match Arena::attach(addr, size) {
        Ok(a) => a,
        Err(_) => return ERR_ARGS,
    };
    let s = match arena.slot(slot as usize) {
        Some(s) => s,
        None => return ERR_ARGS,
    };
    let ev = unsafe {
        std::str::from_utf8(bytes(event as *const u8, event_len)).unwrap_or("")
    };
    let pl = unsafe { bytes(payload, payload_len) };
    match s.write_event(event_id, phase, ev, pl) {
        Ok(()) => OK,
        Err(_) => ERR_BUFFER,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_slot_result(
    addr: u64,
    size: usize,
    slot: i32,
    out: *mut u8,
    out_cap: usize,
    flags_out: *mut u32,
) -> i32 {
    let arena = match Arena::attach(addr, size) {
        Ok(a) => a,
        Err(_) => return ERR_ARGS,
    };
    let s = match arena.slot(slot as usize) {
        Some(s) => s,
        None => return ERR_ARGS,
    };
    if !flags_out.is_null() {
        unsafe { *flags_out = s.flags() };
    }
    write_out(s.output(), out, out_cap)
}

#[no_mangle]
pub extern "C" fn kaptor_slot_reset(addr: u64, size: usize, slot: i32) -> i32 {
    let arena = match Arena::attach(addr, size) {
        Ok(a) => a,
        Err(_) => return ERR_ARGS,
    };
    match arena.slot(slot as usize) {
        Some(s) => {
            s.reset();
            OK
        }
        None => ERR_ARGS,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_attach_arena(
    engine: *mut KaptorEngine,
    addr: u64,
    size: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if addr == 0 || size < ARENA_HEADER_SIZE {
        return ERR_ARGS;
    }
    let arena = if unsafe { std::ptr::read(addr as *const u32) } == arena::ARENA_MAGIC {
        match Arena::attach(addr, size) {
            Ok(a) => a,
            Err(_) => return ERR_ARGS,
        }
    } else {
        let cfg = e.rt.config();
        match Arena::init(addr, size, cfg.type_cap, cfg.payload_cap, cfg.result_cap) {
            Ok(a) => a,
            Err(_) => return ERR_ARGS,
        }
    };
    *lock(&e.arena) = Some(arena);
    arena.layout.slot_count as i32
}

#[no_mangle]
pub extern "C" fn kaptor_engine_layout(engine: *mut KaptorEngine, out: *mut KaptorLayout) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out.is_null() {
        return ERR_ARGS;
    }
    match engine_arena(e) {
        Some(a) => {
            fill_layout(&a.layout, out);
            OK
        }
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_kick(engine: *mut KaptorEngine, slot: i32) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    let arena = match engine_arena(e) {
        Some(a) => a,
        None => return ERR_NOT_INIT,
    };
    let s = match arena.slot(slot as usize) {
        Some(s) => s,
        None => return ERR_ARGS,
    };
    let code = ffi(|| {
        let result = e.rt.process_slot(&s);
        if result.errors.iter().any(|x| x == "slot not pending") {
            ERR_ARGS
        } else if result.errors.is_empty() {
            OK
        } else {
            ERR_TRAP
        }
    });
    e.wake.notify();
    code
}

#[no_mangle]
pub extern "C" fn kaptor_engine_kick_pending(engine: *mut KaptorEngine) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    let arena = match engine_arena(e) {
        Some(a) => a,
        None => return ERR_NOT_INIT,
    };
    ffi(|| {
        let pending = arena.pending();
        let n = pending.len() as i32;
        for idx in pending {
            if let Some(s) = arena.slot(idx) {
                let _ = e.rt.process_slot(&s);
            }
        }
        n
    })
}

#[no_mangle]
pub extern "C" fn kaptor_engine_dispatch(
    engine: *mut KaptorEngine,
    event: *const c_char,
    event_len: usize,
    payload: *const u8,
    payload_len: usize,
    out: *mut u8,
    out_cap: usize,
    status: *mut KaptorStatus,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if event.is_null() {
        return ERR_ARGS;
    }
    let ev = unsafe {
        let raw = bytes(event as *const u8, event_len);
        std::str::from_utf8(raw).unwrap_or("")
    };
    let pl = unsafe { bytes(payload, payload_len) };
    ffi(|| {
        let result = e.rt.dispatch_bytes(ev, pl);
        let code = if result.errors.is_empty() { OK } else { ERR_TRAP };
        fill_status(status, &result, code);
        write_out(&result.payload, out, out_cap)
    })
}

#[no_mangle]
pub extern "C" fn kaptor_engine_dispatch_phase(
    engine: *mut KaptorEngine,
    event: *const c_char,
    event_len: usize,
    phase: *const c_char,
    payload: *const u8,
    payload_len: usize,
    out: *mut u8,
    out_cap: usize,
    status: *mut KaptorStatus,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if event.is_null() {
        return ERR_ARGS;
    }
    let ev = unsafe {
        let raw = bytes(event as *const u8, event_len);
        std::str::from_utf8(raw).unwrap_or("")
    };
    let pl = unsafe { bytes(payload, payload_len) };
    let result = e.rt.dispatch_phase_bytes(ev, unsafe { cstr(phase) }, pl);
    let code = if result.errors.is_empty() { OK } else { ERR_TRAP };
    fill_status(status, &result, code);
    write_out(&result.payload, out, out_cap)
}

#[no_mangle]
pub extern "C" fn kaptor_engine_set_fuel(
    engine: *mut KaptorEngine,
    module_id: i32,
    fuel: u64,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    match e.rt.set_fuel(module_id, fuel) {
        Ok(()) => OK,
        Err(_) => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_was_cancelled(engine: *mut KaptorEngine) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    i32::from(e.rt.was_cancelled())
}

#[no_mangle]
pub extern "C" fn kaptor_engine_last_error(
    engine: *mut KaptorEngine,
    buf: *mut u8,
    cap: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return write_out(b"not initialized", buf, cap);
    };
    write_out(e.rt.last_error().as_bytes(), buf, cap)
}

#[no_mangle]
pub extern "C" fn kaptor_engine_dump_registry(
    engine: *mut KaptorEngine,
    buf: *mut u8,
    cap: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    write_out(e.rt.dump_registry().as_bytes(), buf, cap)
}

#[no_mangle]
pub extern "C" fn kaptor_engine_list_modules(
    engine: *mut KaptorEngine,
    out_ids: *mut i32,
    cap: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out_ids.is_null() && cap != 0 {
        return ERR_ARGS;
    }
    let ids = e.rt.module_ids();
    let n = ids.len().min(cap);
    if !out_ids.is_null() {
        unsafe {
            std::ptr::copy_nonoverlapping(ids.as_ptr(), out_ids, n);
        }
    }
    ids.len() as i32
}

#[no_mangle]
pub extern "C" fn kaptor_engine_list_hooks(
    engine: *mut KaptorEngine,
    out_ids: *mut i32,
    cap: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out_ids.is_null() && cap != 0 {
        return ERR_ARGS;
    }
    let ids = e.rt.hook_ids();
    let n = ids.len().min(cap);
    if !out_ids.is_null() {
        unsafe {
            std::ptr::copy_nonoverlapping(ids.as_ptr(), out_ids, n);
        }
    }
    ids.len() as i32
}

#[no_mangle]
pub extern "C" fn kaptor_engine_module_info(
    engine: *mut KaptorEngine,
    module_id: i32,
    out: *mut KaptorModuleInfo,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out.is_null() {
        return ERR_ARGS;
    }
    match e.rt.module_info(module_id) {
        Some(info) => {
            unsafe { *out = info };
            OK
        }
        None => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_hook_info(
    engine: *mut KaptorEngine,
    hook_id: i32,
    out: *mut KaptorHookInfo,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out.is_null() {
        return ERR_ARGS;
    }
    match e.rt.hook_info(hook_id) {
        Some(info) => {
            unsafe { *out = info };
            OK
        }
        None => ERR_NOT_FOUND,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_engine_stats(engine: *mut KaptorEngine, out: *mut KaptorStats) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    if out.is_null() {
        return ERR_ARGS;
    }
    unsafe { *out = e.rt.stats() };
    OK
}

#[no_mangle]
pub extern "C" fn kaptor_engine_drain_logs(
    engine: *mut KaptorEngine,
    buf: *mut u8,
    cap: usize,
) -> i32 {
    let Some(e) = (unsafe { eng(engine) }) else {
        return ERR_NOT_INIT;
    };
    let logs = e.rt.drain_logs();
    let mut s = String::new();
    for (level, msg) in logs {
        s.push_str(&format!("{level}\t{msg}\n"));
    }
    write_out(s.as_bytes(), buf, cap)
}

fn global() -> Option<&'static KaptorEngine> {
    let g = lock(&GLOBAL);
    unsafe { g.as_ref().and_then(|p| p.0.as_ref()) }
}

#[no_mangle]
pub extern "C" fn kaptor_init(buf_addr: u64, buf_size: usize) -> i32 {
    let mut g = lock(&GLOBAL);
    if g.is_some() {
        return ERR_ALREADY;
    }
    let engine = kaptor_engine_new(std::ptr::null());
    if engine.is_null() {
        return ERR_INSTANTIATE;
    }
    if buf_addr != 0 {
        let cfg = unsafe { &*engine }.rt.config();
        if Arena::init(
            buf_addr,
            buf_size,
            cfg.type_cap,
            cfg.payload_cap,
            cfg.result_cap,
        )
        .is_ok()
        {
            BUFFER_ADDR.store(buf_addr, Ordering::SeqCst);
            BUFFER_SIZE.store(buf_size, Ordering::SeqCst);
            if let Ok(a) = Arena::attach(buf_addr, buf_size) {
                *lock(&unsafe { &*engine }.arena) = Some(a);
            }
            let rt = unsafe { &*engine }.rt.clone();
            let running = unsafe { &*engine }.running.clone();
            let wake = unsafe { &*engine }.wake.clone();
            *lock(&WORKER) = Some(thread::spawn(move || worker_loop(rt, running, wake)));
        } else {
            BUFFER_ADDR.store(0, Ordering::SeqCst);
            BUFFER_SIZE.store(0, Ordering::SeqCst);
        }
    } else {
        BUFFER_ADDR.store(0, Ordering::SeqCst);
        BUFFER_SIZE.store(0, Ordering::SeqCst);
    }
    *g = Some(EnginePtr(engine));
    OK
}

#[no_mangle]
pub extern "C" fn kaptor_shutdown() -> i32 {
    if let Some(e) = global() {
        e.running.store(false, Ordering::SeqCst);
        e.wake.notify();
    }
    if let Some(h) = lock(&WORKER).take() {
        let _ = h.join();
    }
    BUFFER_ADDR.store(0, Ordering::SeqCst);
    BUFFER_SIZE.store(0, Ordering::SeqCst);
    let mut g = lock(&GLOBAL);
    if let Some(p) = g.take() {
        kaptor_engine_free(p.0);
    }
    OK
}

#[no_mangle]
pub extern "C" fn kaptor_load_module(
    name: *const c_char,
    wasm_bytes: *const u8,
    len: usize,
) -> i32 {
    kaptor_engine_load(
        global().map(|e| e as *const _ as *mut _).unwrap_or(std::ptr::null_mut()),
        name,
        wasm_bytes,
        len,
    )
}

#[no_mangle]
pub extern "C" fn kaptor_unload_module(module_id: i32) -> i32 {
    match global() {
        Some(e) => kaptor_engine_unload(e as *const _ as *mut _, module_id),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_reload_module(
    module_id: i32,
    wasm_bytes: *const u8,
    len: usize,
) -> i32 {
    match global() {
        Some(e) => kaptor_engine_reload(e as *const _ as *mut _, module_id, wasm_bytes, len),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_register_hook(
    module_id: i32,
    hook_type: *const c_char,
    event_type: *const c_char,
    export_name: *const c_char,
) -> i32 {
    match global() {
        Some(e) => kaptor_engine_register_hook(
            e as *const _ as *mut _,
            module_id,
            hook_type,
            event_type,
            export_name,
        ),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_unregister_hook(hook_id: i32) -> i32 {
    match global() {
        Some(e) => kaptor_engine_unregister_hook(e as *const _ as *mut _, hook_id),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_discover_hooks(module_id: i32) -> i32 {
    match global() {
        Some(e) => kaptor_engine_discover_hooks(e as *const _ as *mut _, module_id),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_set_fuel(module_id: i32, fuel: u64) -> i32 {
    match global() {
        Some(e) => kaptor_engine_set_fuel(e as *const _ as *mut _, module_id, fuel),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_was_cancelled() -> i32 {
    match global() {
        Some(e) => kaptor_engine_was_cancelled(e as *const _ as *mut _),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_last_error(buf: *mut u8, buf_size: usize) -> i32 {
    match global() {
        Some(e) => kaptor_engine_last_error(e as *const _ as *mut _, buf, buf_size),
        None => write_out(b"not initialized", buf, buf_size),
    }
}

#[no_mangle]
pub extern "C" fn kaptor_dump_registry(buf: *mut u8, buf_size: usize) -> i32 {
    match global() {
        Some(e) => kaptor_engine_dump_registry(e as *const _ as *mut _, buf, buf_size),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_list_modules(out_ids: *mut i32, cap: usize) -> i32 {
    match global() {
        Some(e) => kaptor_engine_list_modules(e as *const _ as *mut _, out_ids, cap),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_submit_event(
    event_type: *const c_char,
    json_data: *const c_char,
    event_id: u64,
) -> i32 {
    if global().is_none() {
        return ERR_NOT_INIT;
    }
    let rb = match buffer() {
        Some(b) => b,
        None => return ERR_NOT_INIT,
    };
    let et = unsafe { cstr(event_type) };
    let jd = unsafe { cstr(json_data) };
    let idx = match rb.claim() {
        Some(idx) => {
            if let Some(slot) = rb.slot(idx) {
                if slot.write_event(event_id, PHASE_ALL, et, jd.as_bytes()).is_err() {
                    slot.reset();
                    return ERR_BUFFER;
                }
            }
            idx as i32
        }
        None => return ERR_QUEUE_FULL,
    };
    if let Some(e) = global() {
        e.wake.notify();
    }
    idx
}

#[no_mangle]
pub extern "C" fn kaptor_wait_result(event_id: u64, timeout_ms: u32) -> i32 {
    let rb = match buffer() {
        Some(b) => b,
        None => return ERR_NOT_INIT,
    };
    let deadline = std::time::Instant::now() + Duration::from_millis(timeout_ms as u64);
    loop {
        if let Some(idx) = rb.find_completed(event_id) {
            return idx as i32;
        }
        if std::time::Instant::now() > deadline {
            return ERR_TIMEOUT;
        }
        thread::sleep(Duration::from_micros(100));
    }
}

#[no_mangle]
pub extern "C" fn kaptor_read_result(slot_index: i32, buf: *mut u8, buf_size: usize) -> i32 {
    if slot_index < 0 || buf.is_null() || buf_size == 0 {
        return ERR_ARGS;
    }
    let rb = match buffer() {
        Some(b) => b,
        None => return ERR_NOT_INIT,
    };
    let slot = match rb.slot(slot_index as usize) {
        Some(s) => s,
        None => return ERR_ARGS,
    };
    let n = write_out(slot.output(), buf, buf_size);
    slot.reset();
    n
}

#[no_mangle]
pub extern "C" fn kaptor_dispatch_sync(
    event_type: *const c_char,
    json_data: *const c_char,
    result_buf: *mut u8,
    result_buf_size: usize,
) -> i32 {
    let et = unsafe { cstr(event_type) };
    let jd = unsafe { cstr(json_data) };
    match global() {
        Some(e) => kaptor_engine_dispatch(
            e as *const _ as *mut _,
            event_type,
            et.len(),
            jd.as_ptr(),
            jd.len(),
            result_buf,
            result_buf_size,
            std::ptr::null_mut(),
        ),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_dispatch_phase(
    event_type: *const c_char,
    phase: *const c_char,
    json_data: *const c_char,
    result_buf: *mut u8,
    result_buf_size: usize,
) -> i32 {
    let et = unsafe { cstr(event_type) };
    let jd = unsafe { cstr(json_data) };
    match global() {
        Some(e) => kaptor_engine_dispatch_phase(
            e as *const _ as *mut _,
            event_type,
            et.len(),
            phase,
            jd.as_ptr(),
            jd.len(),
            result_buf,
            result_buf_size,
            std::ptr::null_mut(),
        ),
        None => ERR_NOT_INIT,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_slot_size() -> usize {
    Layout::default_slot_size()
}

#[no_mangle]
pub extern "C" fn kaptor_header_size() -> usize {
    ARENA_HEADER_SIZE
}

#[no_mangle]
pub extern "C" fn kaptor_magic_done() -> u32 {
    SLOT_DONE
}

#[no_mangle]
pub extern "C" fn kaptor_magic_error() -> u32 {
    SLOT_ERROR
}

#[no_mangle]
pub extern "C" fn kaptor_magic_pending() -> u32 {
    SLOT_PENDING
}

#[no_mangle]
pub extern "C" fn kaptor_magic_empty() -> u32 {
    SLOT_EMPTY
}

#[cfg(test)]
mod tests;
