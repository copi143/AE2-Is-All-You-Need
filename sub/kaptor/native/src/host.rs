use wasmtime::*;

use crate::abi::MEMORY_MAX;

pub struct HostState {
    pub cancelled: bool,
    pub logs: Vec<(i32, String)>,
    pub limits: StoreLimits,
}

impl HostState {
    pub fn new(memory_max: usize) -> Self {
        let limits = StoreLimitsBuilder::new()
            .memory_size(if memory_max == 0 { MEMORY_MAX } else { memory_max })
            .memories(2)
            .tables(4)
            .instances(1)
            .trap_on_grow_failure(true)
            .build();
        HostState {
            cancelled: false,
            logs: Vec::new(),
            limits,
        }
    }

    pub fn reset_dispatch(&mut self) {
        self.cancelled = false;
    }
}

pub fn build_linker(engine: &Engine) -> Result<Linker<HostState>, String> {
    let mut linker = Linker::new(engine);

    linker
        .func_wrap(
            "kaptor",
            "log",
            |mut caller: Caller<'_, HostState>, level: i32, ptr: i32, len: i32| {
                if ptr < 0 || len <= 0 {
                    return;
                }
                let n = (len as usize).min(4096);
                if caller.data().logs.len() >= 64 {
                    return;
                }
                let mem = match caller.get_export("memory") {
                    Some(Extern::Memory(m)) => m,
                    _ => return,
                };
                let mut buf = vec![0u8; n];
                if mem.read(&caller, ptr as usize, &mut buf).is_ok() {
                    let s = String::from_utf8_lossy(&buf).into_owned();
                    caller.data_mut().logs.push((level, s));
                }
            },
        )
        .map_err(|e| e.to_string())?;

    linker
        .func_wrap("kaptor", "cancel", |mut caller: Caller<'_, HostState>| {
            caller.data_mut().cancelled = true;
        })
        .map_err(|e| e.to_string())?;

    linker
        .func_wrap(
            "kaptor",
            "is_cancelled",
            |caller: Caller<'_, HostState>| -> i32 {
                i32::from(caller.data().cancelled)
            },
        )
        .map_err(|e| e.to_string())?;

    Ok(linker)
}
