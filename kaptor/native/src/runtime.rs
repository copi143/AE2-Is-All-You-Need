use wasmtime::*;
use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::atomic::{AtomicI32, Ordering};

const WASM_MEMORY_BASE_EVENT_TYPE: usize = 0;
const WASM_MEMORY_BASE_EVENT_DATA: usize = 256;
const WASM_MEMORY_BASE_RESULT: usize = 4352;
const WASM_RESULT_BUF_SIZE: i32 = 4096;

#[derive(Clone)]
pub struct LoadedModule {
    #[allow(dead_code)]
    pub name: String,
    pub module: Module,
}

#[derive(Clone)]
pub struct NativeHook {
    pub module_id: i32,
    pub hook_type: String,
    pub event_type: String,
    pub export_name: String,
}

pub struct WasmtimeRuntime {
    engine: Engine,
    modules: Mutex<HashMap<i32, LoadedModule>>,
    hooks: Mutex<Vec<NativeHook>>,
    next_module_id: AtomicI32,
    next_hook_id: AtomicI32,
}

impl WasmtimeRuntime {
    pub fn new() -> Result<Self, String> {
        let mut config = Config::new();
        config.cranelift_nan_canonicalization(false);
        config.wasm_backtrace_details(WasmBacktraceDetails::Disable);
        let engine = Engine::new(&config).map_err(|e| e.to_string())?;

        Ok(WasmtimeRuntime {
            engine,
            modules: Mutex::new(HashMap::new()),
            hooks: Mutex::new(Vec::new()),
            next_module_id: AtomicI32::new(0),
            next_hook_id: AtomicI32::new(0),
        })
    }

    pub fn load_module(&self, name: &str, wasm_bytes: &[u8]) -> Result<i32, String> {
        let module = Module::new(&self.engine, wasm_bytes).map_err(|e| e.to_string())?;
        let id = self.next_module_id.fetch_add(1, Ordering::SeqCst);
        let mut modules = self.modules.lock().unwrap();
        modules.insert(id, LoadedModule {
            name: name.to_string(),
            module,
        });
        Ok(id)
    }

    pub fn register_hook(
        &self,
        module_id: i32,
        hook_type: &str,
        event_type: &str,
        export_name: &str,
    ) -> Result<i32, String> {
        let modules = self.modules.lock().unwrap();
        if !modules.contains_key(&module_id) {
            return Err("module not found".to_string());
        }
        drop(modules);

        let id = self.next_hook_id.fetch_add(1, Ordering::SeqCst);
        let mut hooks = self.hooks.lock().unwrap();
        hooks.push(NativeHook {
            module_id,
            hook_type: hook_type.to_string(),
            event_type: event_type.to_string(),
            export_name: export_name.to_string(),
        });
        Ok(id)
    }

    pub fn dispatch_event(
        &self,
        event_type: &str,
        hook_types: &[&str],
        event_json: &str,
    ) -> Result<String, String> {
        let hooks_match: Vec<NativeHook> = {
            let hooks = self.hooks.lock().unwrap();
            hooks
                .iter()
                .filter(|h| h.event_type == event_type && hook_types.contains(&h.hook_type.as_str()))
                .cloned()
                .collect()
        };

        let mut result_json = String::new();

        for hook in hooks_match {
            let module = {
                let modules = self.modules.lock().unwrap();
                modules.get(&hook.module_id).map(|m| m.module.clone())
            };
            let module = match module {
                Some(m) => m,
                None => continue,
            };

            let mut store = Store::new(&self.engine, ());

            let instance = Instance::new(&mut store, &module, &[])
                .map_err(|e| format!("instantiate: {}", e))?;

            let handle: TypedFunc<(i32, i32, i32, i32, i32, i32), i32> = instance
                .get_typed_func(&mut store, &hook.export_name)
                .map_err(|e| format!("export '{}': {}", hook.export_name, e))?;

            let memory = instance
                .get_memory(&mut store, "memory")
                .ok_or("no 'memory' export")?;

            // Write event_type (null-terminated)
            let type_bytes = event_type.as_bytes();
            let type_len = type_bytes.len().min(255) as i32;
            memory.write(&mut store, WASM_MEMORY_BASE_EVENT_TYPE, &type_bytes[..type_len as usize])
                .map_err(|e| e.to_string())?;
            memory.write(&mut store, WASM_MEMORY_BASE_EVENT_TYPE + type_len as usize, &[0u8])
                .map_err(|e| e.to_string())?;

            // Write event_data JSON (null-terminated)
            let data_bytes = event_json.as_bytes();
            let data_len = data_bytes.len().min(4095) as i32;
            memory.write(&mut store, WASM_MEMORY_BASE_EVENT_DATA, &data_bytes[..data_len as usize])
                .map_err(|e| e.to_string())?;
            memory.write(&mut store, WASM_MEMORY_BASE_EVENT_DATA + data_len as usize, &[0u8])
                .map_err(|e| e.to_string())?;

            // Zero the result buffer
            memory.write(&mut store, WASM_MEMORY_BASE_RESULT, &[0u8; 4096])
                .map_err(|e| e.to_string())?;

            let result_len = handle
                .call(&mut store, (
                    WASM_MEMORY_BASE_EVENT_TYPE as i32,
                    type_len,
                    WASM_MEMORY_BASE_EVENT_DATA as i32,
                    data_len,
                    WASM_MEMORY_BASE_RESULT as i32,
                    WASM_RESULT_BUF_SIZE,
                ))
                .map_err(|e| format!("trap in '{}': {}", hook.export_name, e))?;

            if result_len > 0 {
                let mut buf = vec![0u8; result_len as usize];
                memory.read(&store, WASM_MEMORY_BASE_RESULT, &mut buf)
                    .map_err(|e| e.to_string())?;
                result_json = String::from_utf8_lossy(&buf).to_string();
            }
        }

        Ok(result_json)
    }
}
