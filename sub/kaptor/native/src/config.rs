use crate::abi::{
    DEFAULT_FUEL, EPOCH_DEADLINE_TICKS, EPOCH_INTERVAL_MS, FLAG_CANCELLED, MEMORY_MAX,
};

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct KaptorConfig {
    pub struct_size: u32,
    pub flags: u32,
    pub fuel_default: u64,
    pub epoch_ms: u32,
    pub epoch_ticks: u32,
    pub memory_max: u32,
    pub reserved: u32,
    pub type_cap: u32,
    pub payload_cap: u32,
    pub result_cap: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct KaptorStatus {
    pub code: i32,
    pub flags: u32,
    pub invoked: u32,
    pub error_count: u32,
    pub fuel_used: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct KaptorStats {
    pub invokes: u64,
    pub traps: u64,
    pub fuel_used: u64,
    pub modules: u64,
    pub hooks: u64,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct KaptorModuleInfo {
    pub id: i32,
    pub name: [u8; 64],
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct KaptorHookInfo {
    pub id: i32,
    pub module_id: i32,
    pub phase: [u8; 16],
    pub event: [u8; 128],
    pub export_name: [u8; 64],
}

#[derive(Clone, Debug)]
pub struct EngineConfig {
    pub fuel_default: u64,
    pub epoch_interval_ms: u64,
    pub epoch_deadline_ticks: u64,
    pub memory_max: usize,
    pub start_epoch: bool,
    pub type_cap: u32,
    pub payload_cap: u32,
    pub result_cap: u32,
}

impl Default for EngineConfig {
    fn default() -> Self {
        EngineConfig {
            fuel_default: DEFAULT_FUEL,
            epoch_interval_ms: EPOCH_INTERVAL_MS,
            epoch_deadline_ticks: EPOCH_DEADLINE_TICKS,
            memory_max: MEMORY_MAX,
            start_epoch: true,
            type_cap: crate::arena::DEFAULT_TYPE_CAP as u32,
            payload_cap: crate::arena::DEFAULT_PAYLOAD_CAP as u32,
            result_cap: crate::arena::DEFAULT_RESULT_CAP as u32,
        }
    }
}

impl EngineConfig {
    pub fn from_c(cfg: Option<&KaptorConfig>) -> Self {
        let Some(c) = cfg else {
            return Self::default();
        };
        if c.struct_size as usize != std::mem::size_of::<KaptorConfig>() && c.struct_size != 0 {
            let mut out = Self::default();
            if c.fuel_default > 0 {
                out.fuel_default = c.fuel_default;
            }
            return out;
        }
        let mut out = EngineConfig {
            fuel_default: if c.fuel_default == 0 {
                DEFAULT_FUEL
            } else {
                c.fuel_default
            },
            epoch_interval_ms: if c.epoch_ms == 0 {
                EPOCH_INTERVAL_MS
            } else {
                c.epoch_ms as u64
            },
            epoch_deadline_ticks: if c.epoch_ticks == 0 {
                EPOCH_DEADLINE_TICKS
            } else {
                c.epoch_ticks as u64
            },
            memory_max: if c.memory_max == 0 {
                MEMORY_MAX
            } else {
                c.memory_max as usize
            },
            start_epoch: c.flags & crate::abi::CFG_NO_EPOCH == 0,
            type_cap: crate::arena::DEFAULT_TYPE_CAP as u32,
            payload_cap: crate::arena::DEFAULT_PAYLOAD_CAP as u32,
            result_cap: crate::arena::DEFAULT_RESULT_CAP as u32,
        };
        if c.struct_size as usize >= std::mem::size_of::<KaptorConfig>() {
            if c.type_cap > 0 {
                out.type_cap = c.type_cap;
            }
            if c.payload_cap > 0 {
                out.payload_cap = c.payload_cap;
            }
            if c.result_cap > 0 {
                out.result_cap = c.result_cap;
            }
        }
        out
    }
}

impl KaptorStatus {
    pub fn from_dispatch(code: i32, invoked: u32, errors: u32, cancelled: bool, fuel_used: u64) -> Self {
        KaptorStatus {
            code,
            flags: if cancelled { FLAG_CANCELLED } else { 0 },
            invoked,
            error_count: errors,
            fuel_used,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct KaptorLayout {
    pub struct_size: u32,
    pub header_size: u32,
    pub slot_hdr_size: u32,
    pub slot_size: u32,
    pub slot_count: u32,
    pub type_off: u32,
    pub payload_off: u32,
    pub result_off: u32,
    pub type_cap: u32,
    pub payload_cap: u32,
    pub result_cap: u32,
}

pub fn write_cstr_field(dst: &mut [u8], src: &str) {
    dst.fill(0);
    let bytes = src.as_bytes();
    let n = bytes.len().min(dst.len().saturating_sub(1));
    dst[..n].copy_from_slice(&bytes[..n]);
}
