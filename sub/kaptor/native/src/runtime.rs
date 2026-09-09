use std::cell::RefCell;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};

use wasmtime::*;

use crate::arena::{
    scratch_arena, Arena, Slot, PHASE_AFTER, PHASE_BEFORE, PHASE_ON, SLOT_DONE, SLOT_ERROR,
    SLOT_FLAG_CANCELLED, SLOT_PENDING, SLOT_RUNNING,
};
use crate::config::{
    write_cstr_field, EngineConfig, KaptorHookInfo, KaptorModuleInfo, KaptorStats,
};
use crate::dispatch::{should_skip, DispatchResult};
use crate::hooks::{
    classify_hook, is_hook_signature, parse_export_name, parse_manifest, Hook, HookAbi, HookPhase,
    HookRegistry,
};
use crate::host::{build_linker, HostState};

const ALLOWED_IMPORTS: &[(&str, &str)] = &[
    ("kaptor", "log"),
    ("kaptor", "cancel"),
    ("kaptor", "is_cancelled"),
];

enum CachedHook {
    Slot(TypedFunc<i32, i32>),
    Legacy(TypedFunc<(i32, i32, i32, i32, i32, i32), i32>),
}

pub struct Live {
    store: Store<HostState>,
    instance: Instance,
    memory: Memory,
    has_alloc: bool,
    hooks: HashMap<String, CachedHook>,
    alloc: Option<TypedFunc<i32, i32>>,
    free: Option<TypedFunc<i32, ()>>,
}

fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

thread_local! {
    static TLS_SCRATCH: RefCell<Option<(u32, u32, u32, Vec<u8>, Arena)>> = const { RefCell::new(None) };
}

pub struct ModuleSlot {
    pub id: i32,
    pub name: Mutex<String>,
    compiled: Mutex<Module>,
    live: Mutex<Live>,
    pub fuel_limit: AtomicU64,
}

pub struct KaptorRuntime {
    engine: Engine,
    config: EngineConfig,
    modules: Mutex<HashMap<i32, Arc<ModuleSlot>>>,
    hooks: Mutex<HookRegistry>,
    next_module_id: AtomicI32,
    last_cancelled: AtomicBool,
    last_error: Mutex<String>,
    logs: Mutex<Vec<(i32, String)>>,
    invokes: AtomicU64,
    traps: AtomicU64,
    fuel_used: AtomicU64,
    linker: Linker<HostState>,
}

impl KaptorRuntime {
    #[allow(dead_code)]
    pub fn new() -> Result<Self, String> {
        Self::with_config(EngineConfig::default())
    }

    pub fn with_config(config: EngineConfig) -> Result<Self, String> {
        let mut wt = Config::new();
        wt.consume_fuel(true);
        wt.epoch_interruption(true);
        wt.wasm_backtrace_details(WasmBacktraceDetails::Disable);
        wt.cranelift_nan_canonicalization(false);
        wt.cranelift_opt_level(OptLevel::Speed);
        wt.max_wasm_stack(256 * 1024);
        wt.wasm_multi_memory(false);
        wt.memory_reservation(1 << 32);
        wt.memory_guard_size(32 << 20);
        let engine = Engine::new(&wt).map_err(|e| e.to_string())?;
        let linker = build_linker(&engine)?;
        Ok(KaptorRuntime {
            engine,
            config,
            modules: Mutex::new(HashMap::new()),
            hooks: Mutex::new(HookRegistry::new()),
            next_module_id: AtomicI32::new(0),
            last_cancelled: AtomicBool::new(false),
            last_error: Mutex::new(String::new()),
            logs: Mutex::new(Vec::new()),
            invokes: AtomicU64::new(0),
            traps: AtomicU64::new(0),
            fuel_used: AtomicU64::new(0),
            linker,
        })
    }

    pub fn config(&self) -> &EngineConfig {
        &self.config
    }

    pub fn engine(&self) -> &Engine {
        &self.engine
    }

    pub fn last_error(&self) -> String {
        lock(&self.last_error).clone()
    }

    pub fn was_cancelled(&self) -> bool {
        self.last_cancelled.load(Ordering::SeqCst)
    }

    pub fn drain_logs(&self) -> Vec<(i32, String)> {
        std::mem::take(&mut *lock(&self.logs))
    }

    pub fn stats(&self) -> KaptorStats {
        KaptorStats {
            invokes: self.invokes.load(Ordering::SeqCst),
            traps: self.traps.load(Ordering::SeqCst),
            fuel_used: self.fuel_used.load(Ordering::SeqCst),
            modules: lock(&self.modules).len() as u64,
            hooks: lock(&self.hooks).all().len() as u64,
        }
    }

    fn set_error(&self, msg: impl Into<String>) {
        *lock(&self.last_error) = msg.into();
    }

    fn clear_error(&self) {
        lock(&self.last_error).clear();
    }

    fn check_imports(module: &Module) -> Result<(), String> {
        for imp in module.imports() {
            let ok = ALLOWED_IMPORTS
                .iter()
                .any(|(m, n)| imp.module() == *m && imp.name() == *n);
            if !ok {
                return Err(format!(
                    "forbidden import {}::{}",
                    imp.module(),
                    imp.name()
                ));
            }
        }
        Ok(())
    }

    fn instantiate(&self, module: &Module) -> Result<Live, String> {
        Self::check_imports(module)?;
        let mut store = Store::new(&self.engine, HostState::new(self.config.memory_max));
        store.limiter(|s| &mut s.limits);
        let _ = store.set_fuel(self.config.fuel_default);
        store.epoch_deadline_trap();
        store.set_epoch_deadline(u64::MAX);

        let instance = self
            .linker
            .instantiate(&mut store, module)
            .map_err(|e| format!("instantiate: {e}"))?;
        let memory = instance
            .get_memory(&mut store, "memory")
            .ok_or_else(|| "no 'memory' export".to_string())?;
        let alloc = instance
            .get_typed_func::<i32, i32>(&mut store, "kaptor_alloc")
            .ok();
        let free = instance
            .get_typed_func::<i32, ()>(&mut store, "kaptor_free")
            .ok();
        let mut hooks = HashMap::new();
        for exp in module.exports() {
            let Some(abi) = classify_hook(&exp.ty()) else {
                continue;
            };
            let name = exp.name().to_string();
            match abi {
                HookAbi::Slot => {
                    if let Ok(f) = instance.get_typed_func(&mut store, &name) {
                        hooks.insert(name, CachedHook::Slot(f));
                    }
                }
                HookAbi::Legacy => {
                    if let Ok(f) = instance.get_typed_func(&mut store, &name) {
                        hooks.insert(name, CachedHook::Legacy(f));
                    }
                }
            }
        }
        Ok(Live {
            store,
            instance,
            memory,
            has_alloc: alloc.is_some(),
            hooks,
            alloc,
            free,
        })
    }

    fn find_id_by_name(&self, name: &str) -> Option<i32> {
        if name.is_empty() {
            return None;
        }
        let modules = lock(&self.modules);
        for slot in modules.values() {
            if lock(&slot.name).as_str() == name {
                return Some(slot.id);
            }
        }
        None
    }

    fn export_is_hook(module: &Module, export_name: &str) -> bool {
        module.exports().any(|exp| {
            exp.name() == export_name && is_hook_signature(&exp.ty())
        })
    }

    pub fn load_module(&self, name: &str, bytes: &[u8]) -> Result<i32, String> {
        self.clear_error();
        if bytes.is_empty() {
            self.set_error("empty wasm");
            return Err("empty wasm".into());
        }
        if let Some(id) = self.find_id_by_name(name) {
            self.reload_module(id, bytes)?;
            return Ok(id);
        }

        let compiled = Module::new(&self.engine, bytes).map_err(|e| {
            let msg = format!("compile: {e}");
            self.set_error(&msg);
            msg
        })?;
        let live = self.instantiate(&compiled).map_err(|e| {
            self.set_error(&e);
            e
        })?;

        let id = self.next_module_id.fetch_add(1, Ordering::SeqCst);
        let slot = Arc::new(ModuleSlot {
            id,
            name: Mutex::new(name.to_string()),
            compiled: Mutex::new(compiled),
            live: Mutex::new(live),
            fuel_limit: AtomicU64::new(self.config.fuel_default),
        });

        lock(&self.modules).insert(id, Arc::clone(&slot));
        if let Err(e) = self.discover_into(&slot) {
            self.set_error(&e);
        }
        Ok(id)
    }

    pub fn unload_module(&self, id: i32) -> Result<(), String> {
        self.clear_error();
        let removed = lock(&self.modules).remove(&id);
        if removed.is_none() {
            self.set_error("module not found");
            return Err("module not found".into());
        }
        lock(&self.hooks).remove_module(id);
        Ok(())
    }

    pub fn reload_module(&self, id: i32, bytes: &[u8]) -> Result<(), String> {
        self.clear_error();
        let slot = {
            let modules = lock(&self.modules);
            modules.get(&id).cloned()
        };
        let slot = match slot {
            Some(s) => s,
            None => {
                self.set_error("module not found");
                return Err("module not found".into());
            }
        };

        let compiled = Module::new(&self.engine, bytes).map_err(|e| {
            let msg = format!("compile: {e}");
            self.set_error(&msg);
            msg
        })?;
        let live = self.instantiate(&compiled).map_err(|e| {
            self.set_error(&e);
            e
        })?;

        *lock(&slot.compiled) = compiled;
        *lock(&slot.live) = live;
        lock(&self.hooks).remove_module(id);
        self.discover_into(&slot)?;
        Ok(())
    }

    pub fn register_hook(
        &self,
        module_id: i32,
        phase: &str,
        event_type: &str,
        export_name: &str,
    ) -> Result<i32, String> {
        self.clear_error();
        let phase = HookPhase::parse(phase).ok_or_else(|| {
            self.set_error("invalid hook phase");
            "invalid hook phase".to_string()
        })?;
        if event_type.is_empty() || export_name.is_empty() {
            self.set_error("empty event or export");
            return Err("empty event or export".into());
        }
        {
            let modules = lock(&self.modules);
            let slot = modules.get(&module_id).ok_or_else(|| {
                self.set_error("module not found");
                "module not found".to_string()
            })?;
            let compiled = lock(&slot.compiled);
            if !Self::export_is_hook(&compiled, export_name) {
                self.set_error("export missing or bad signature");
                return Err("export missing or bad signature".into());
            }
        }
        Ok(self
            .hooks
            .lock()
            .unwrap()
            .add(module_id, phase, event_type, export_name))
    }

    pub fn unregister_hook(&self, hook_id: i32) -> Result<(), String> {
        self.clear_error();
        if !lock(&self.hooks).remove_id(hook_id) {
            self.set_error("hook not found");
            return Err("hook not found".into());
        }
        Ok(())
    }

    pub fn discover_hooks(&self, module_id: i32) -> Result<Vec<i32>, String> {
        self.clear_error();
        let slot = {
            let modules = lock(&self.modules);
            modules.get(&module_id).cloned()
        };
        let slot = match slot {
            Some(s) => s,
            None => {
                self.set_error("module not found");
                return Err("module not found".into());
            }
        };
        self.discover_into(&slot)
    }

    fn discover_into(&self, slot: &ModuleSlot) -> Result<Vec<i32>, String> {
        let mut found: Vec<(HookPhase, String, String)> = Vec::new();
        {
            let compiled = lock(&slot.compiled);
            for exp in compiled.exports() {
                let name = exp.name();
                if let Some((phase, event)) = parse_export_name(name) {
                    if is_hook_signature(&exp.ty()) {
                        found.push((phase, event, name.to_string()));
                    }
                }
            }
        }

        if let Ok(json) = call_manifest(&mut lock(&slot.live)) {
            for (phase, event, export) in parse_manifest(&json) {
                found.retain(|x| x.2 != export);
                found.push((phase, event, export));
            }
        }

        let mut ids = Vec::new();
        let mut hooks = lock(&self.hooks);
        for (phase, event, export) in found {
            ids.push(hooks.add(slot.id, phase, &event, &export));
        }
        Ok(ids)
    }

    pub fn set_fuel(&self, module_id: i32, fuel: u64) -> Result<(), String> {
        self.clear_error();
        let modules = lock(&self.modules);
        let slot = modules.get(&module_id).ok_or_else(|| {
            self.set_error("module not found");
            "module not found".to_string()
        })?;
        slot.fuel_limit.store(fuel, Ordering::SeqCst);
        Ok(())
    }

    pub fn module_ids(&self) -> Vec<i32> {
        let mut ids: Vec<i32> = lock(&self.modules).keys().copied().collect();
        ids.sort_unstable();
        ids
    }

    pub fn hook_ids(&self) -> Vec<i32> {
        lock(&self.hooks).ids()
    }

    #[allow(dead_code)]
    pub fn hooks_snapshot(&self) -> Vec<Hook> {
        lock(&self.hooks).all().to_vec()
    }

    pub fn module_info(&self, id: i32) -> Option<KaptorModuleInfo> {
        let modules = lock(&self.modules);
        let slot = modules.get(&id)?;
        let name = lock(&slot.name);
        let mut info = KaptorModuleInfo {
            id,
            name: [0; 64],
        };
        write_cstr_field(&mut info.name, &name);
        Some(info)
    }

    pub fn hook_info(&self, id: i32) -> Option<KaptorHookInfo> {
        let hooks = lock(&self.hooks);
        let h = hooks.get(id)?;
        let mut info = KaptorHookInfo {
            id: h.id,
            module_id: h.module_id,
            phase: [0; 16],
            event: [0; 128],
            export_name: [0; 64],
        };
        write_cstr_field(&mut info.phase, h.phase.as_str());
        write_cstr_field(&mut info.event, &h.event_type);
        write_cstr_field(&mut info.export_name, &h.export_name);
        Some(info)
    }

    pub fn dump_registry(&self) -> String {
        let modules = lock(&self.modules);
        let hooks = lock(&self.hooks);
        let mut module_json = Vec::new();
        for id in {
            let mut ids: Vec<i32> = modules.keys().copied().collect();
            ids.sort_unstable();
            ids
        } {
            let slot = &modules[&id];
            let name = lock(&slot.name).clone();
            module_json.push(format!(
                "{{\"id\":{id},\"name\":{}}}",
                json_escape(&name)
            ));
        }
        let mut hook_json = Vec::new();
        for h in hooks.all() {
            hook_json.push(format!(
                "{{\"id\":{},\"module\":{},\"phase\":{},\"event\":{},\"export\":{}}}",
                h.id,
                h.module_id,
                json_escape(h.phase.as_str()),
                json_escape(&h.event_type),
                json_escape(&h.export_name)
            ));
        }
        format!(
            "{{\"modules\":[{}],\"hooks\":[{}]}}",
            module_json.join(","),
            hook_json.join(",")
        )
    }

    #[allow(dead_code)]
    pub fn dispatch(&self, event_type: &str, payload: &str) -> DispatchResult {
        self.dispatch_bytes(event_type, payload.as_bytes())
    }

    pub fn dispatch_bytes(&self, event_type: &str, payload: &[u8]) -> DispatchResult {
        self.dispatch_on_scratch(event_type, payload, 0)
    }

    #[allow(dead_code)]
    pub fn dispatch_phase(
        &self,
        event_type: &str,
        phase: &str,
        payload: &str,
    ) -> DispatchResult {
        self.dispatch_phase_bytes(event_type, phase, payload.as_bytes())
    }

    pub fn dispatch_phase_bytes(
        &self,
        event_type: &str,
        phase: &str,
        payload: &[u8],
    ) -> DispatchResult {
        let code = match HookPhase::parse(phase) {
            Some(HookPhase::Before) => PHASE_BEFORE,
            Some(HookPhase::On) => PHASE_ON,
            Some(HookPhase::After) => PHASE_AFTER,
            None => {
                self.set_error("invalid hook phase");
                return DispatchResult::empty(payload);
            }
        };
        self.dispatch_on_scratch(event_type, payload, code)
    }

    fn dispatch_on_scratch(&self, event_type: &str, payload: &[u8], phase: u32) -> DispatchResult {
        let tc = self.config.type_cap;
        let pc = self.config.payload_cap;
        let rc = self.config.result_cap;
        TLS_SCRATCH.with(|cell| {
            let mut holder = cell.borrow_mut();
            let recreate = match holder.as_ref() {
                Some((t, p, r, _, _)) => *t != tc || *p != pc || *r != rc,
                None => true,
            };
            if recreate {
                let (buf, arena) = scratch_arena(tc, pc, rc);
                *holder = Some((tc, pc, rc, buf, arena));
            }
            let slot = holder.as_ref().and_then(|h| h.4.slot(0));
            let slot = match slot {
                Some(s) => s,
                None => {
                    self.set_error("scratch arena missing");
                    return DispatchResult::empty(payload);
                }
            };
            slot.reset();
            if let Err(e) = slot.write_event(0, phase, event_type, payload) {
                self.set_error(&e);
                let mut r = DispatchResult::empty(payload);
                r.errors.push(e);
                return r;
            }
            self.process_slot(&slot)
        })
    }

    pub fn process_slot(&self, slot: &Slot) -> DispatchResult {
        self.clear_error();
        if !slot.cas_magic(SLOT_PENDING, SLOT_RUNNING) {
            self.set_error("slot not pending");
            let mut r = DispatchResult::empty(slot.payload());
            r.errors.push("slot not pending".into());
            return r;
        }

        let event_type = slot.event_type_str().to_string();
        let hooks = {
            let reg = lock(&self.hooks);
            match slot.phase() {
                PHASE_BEFORE => reg.for_event_phase(&event_type, HookPhase::Before),
                PHASE_ON => reg.for_event_phase(&event_type, HookPhase::On),
                PHASE_AFTER => reg.for_event_phase(&event_type, HookPhase::After),
                _ => reg.for_event(&event_type),
            }
        };

        let mut result = DispatchResult::empty(slot.payload());
        for hook in hooks {
            if should_skip(hook.phase, result.cancelled) {
                continue;
            }
            let module = {
                let modules = lock(&self.modules);
                modules.get(&hook.module_id).cloned()
            };
            let module = match module {
                Some(s) => s,
                None => {
                    result.errors.push(format!("module {} gone", hook.module_id));
                    continue;
                }
            };
            let fuel = module.fuel_limit.load(Ordering::SeqCst);
            let mut live = lock(&module.live);
            match invoke_mailbox(
                &mut live,
                &hook.export_name,
                slot,
                fuel,
                self.config.epoch_deadline_ticks,
            ) {
                Ok(inv) => {
                    result.invoked += 1;
                    result.fuel_used += inv.fuel_used;
                    self.invokes.fetch_add(1, Ordering::SeqCst);
                    self.fuel_used.fetch_add(inv.fuel_used, Ordering::SeqCst);
                    if inv.cancelled {
                        result.cancelled = true;
                        slot.set_flags(slot.flags() | SLOT_FLAG_CANCELLED);
                    }
                    if slot.result_len() > 0 {
                        slot.promote_result_to_payload();
                    }
                    result.payload = slot.payload().to_vec();
                    if !inv.logs.is_empty() {
                        lock(&self.logs).extend(inv.logs);
                    }
                }
                Err(e) => {
                    result.traps += 1;
                    self.traps.fetch_add(1, Ordering::SeqCst);
                    self.set_error(&e);
                    result.errors.push(e);
                }
            }
        }

        result.cancelled |= slot.flags() & SLOT_FLAG_CANCELLED != 0;
        self.last_cancelled
            .store(result.cancelled, Ordering::SeqCst);
        if result.errors.is_empty() {
            slot.set_magic(SLOT_DONE);
        } else {
            slot.set_magic(SLOT_ERROR);
        }
        result
    }
}

struct InvokeOut {
    cancelled: bool,
    fuel_used: u64,
    logs: Vec<(i32, String)>,
}

fn invoke_mailbox(
    live: &mut Live,
    export: &str,
    slot: &Slot,
    fuel: u64,
    epoch_ticks: u64,
) -> Result<InvokeOut, String> {
    live.store.data_mut().reset_dispatch();
    live.store
        .set_fuel(fuel)
        .map_err(|e| format!("fuel: {e}"))?;
    live.store.set_epoch_deadline(epoch_ticks);

    let layout = slot.layout;
    let mailbox_size = layout.mailbox_size();
    let (base, allocated) = if live.has_alloc {
        let ptr = cached_alloc(live, mailbox_size as i32)?;
        if ptr < 64 {
            return Err("kaptor_alloc returned low pointer".into());
        }
        (ptr as usize, Some(ptr))
    } else {
        let base = crate::arena::GUEST_MAILBOX_BASE;
        ensure_memory(live, base + mailbox_size)?;
        (base, None)
    };

    live.memory
        .write(&mut live.store, base, slot.as_bytes())
        .map_err(|e| e.to_string())?;
    write_u32(live, base, layout.type_cap)?;
    write_u32(live, base + 8, layout.payload_cap)?;
    write_u32(live, base + 12, layout.result_cap)?;

    match live.hooks.get(export) {
        Some(CachedHook::Slot(f)) => {
            f.call(&mut live.store, base as i32)
                .map_err(|e| format!("trap in '{export}': {e}"))?;
        }
        Some(CachedHook::Legacy(f)) => {
            let result_len = f
                .call(
                    &mut live.store,
                    (
                        (base + layout.type_off as usize) as i32,
                        slot.type_len() as i32,
                        (base + layout.payload_off as usize) as i32,
                        slot.payload_len() as i32,
                        (base + layout.result_off as usize) as i32,
                        layout.result_cap as i32,
                    ),
                )
                .map_err(|e| format!("trap in '{export}': {e}"))?;
            if result_len > 0 {
                write_u32(live, base + 28, result_len as u32)?;
            }
        }
        None => return Err(format!("export '{export}' not cached")),
    }

    let remaining = live.store.get_fuel().unwrap_or(0);
    let used = fuel.saturating_sub(remaining);
    let mut cancelled = live.store.data().cancelled;
    let logs = std::mem::take(&mut live.store.data_mut().logs);

    let flags = read_u32(live, base + 4)?;
    if flags & SLOT_FLAG_CANCELLED != 0 {
        cancelled = true;
    }
    let result_len = read_u32(live, base + 28)? as usize;
    if result_len > 0 {
        let n = result_len.min(layout.result_cap as usize);
        let result_ptr = base + layout.result_off as usize;
        let mut buf = vec![0u8; n];
        live.memory
            .read(&live.store, result_ptr, &mut buf)
            .map_err(|e| e.to_string())?;
        slot.write_result(&buf);
    }

    if let Some(ptr) = allocated {
        cached_free(live, ptr);
    }

    Ok(InvokeOut {
        cancelled,
        fuel_used: used,
        logs,
    })
}

fn write_u32(live: &mut Live, addr: usize, v: u32) -> Result<(), String> {
    live.memory
        .write(&mut live.store, addr, &v.to_le_bytes())
        .map_err(|e| e.to_string())
}

fn read_u32(live: &mut Live, addr: usize) -> Result<u32, String> {
    let mut buf = [0u8; 4];
    live.memory
        .read(&live.store, addr, &mut buf)
        .map_err(|e| e.to_string())?;
    Ok(u32::from_le_bytes(buf))
}

fn ensure_memory(live: &mut Live, bytes: usize) -> Result<(), String> {
    let need = (bytes as u64 + 65535) / 65536;
    let have = live.memory.size(&live.store);
    if have < need {
        live.memory
            .grow(&mut live.store, need - have)
            .map_err(|e| format!("memory.grow: {e}"))?;
    }
    Ok(())
}

fn cached_alloc(live: &mut Live, size: i32) -> Result<i32, String> {
    let f = live
        .alloc
        .clone()
        .ok_or_else(|| "no kaptor_alloc".to_string())?;
    let ptr = f.call(&mut live.store, size).map_err(|e| e.to_string())?;
    if ptr <= 0 {
        return Err("kaptor_alloc returned 0".into());
    }
    Ok(ptr)
}

fn cached_free(live: &mut Live, ptr: i32) {
    if let Some(f) = live.free.clone() {
        let _ = f.call(&mut live.store, ptr);
    }
}

fn call_manifest(live: &mut Live) -> Result<String, String> {
    let func: TypedFunc<(), (i32, i32)> = live
        .instance
        .get_typed_func(&mut live.store, "kaptor_manifest")
        .map_err(|e| e.to_string())?;
    let (ptr, len) = func.call(&mut live.store, ()).map_err(|e| e.to_string())?;
    if ptr < 0 || len <= 0 {
        return Ok(String::new());
    }
    let n = (len as usize).min(65536);
    let mut buf = vec![0u8; n];
    live.memory
        .read(&live.store, ptr as usize, &mut buf)
        .map_err(|e| e.to_string())?;
    String::from_utf8(buf).map_err(|e| e.to_string())
}

fn json_escape(s: &str) -> String {
    serde_json::to_string(s).unwrap_or_else(|_| "\"\"".into())
}
