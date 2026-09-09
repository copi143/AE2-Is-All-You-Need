pub const ABI_VERSION: i32 = 3;

pub const DEFAULT_FUEL: u64 = 1_000_000;
pub const MEMORY_MAX: usize = 16 * 1024 * 1024;
pub const EPOCH_INTERVAL_MS: u64 = 50;
pub const EPOCH_DEADLINE_TICKS: u64 = 2;

pub const FLAG_CANCELLED: u32 = 1;
pub const CFG_NO_EPOCH: u32 = 1;

pub const OK: i32 = 0;
pub const ERR_ARGS: i32 = -1;
pub const ERR_NOT_INIT: i32 = -2;
pub const ERR_NOT_FOUND: i32 = -3;
pub const ERR_COMPILE: i32 = -5;
pub const ERR_INSTANTIATE: i32 = -6;
pub const ERR_TRAP: i32 = -7;
pub const ERR_BUFFER: i32 = -8;
pub const ERR_QUEUE_FULL: i32 = -9;
pub const ERR_TIMEOUT: i32 = -10;
pub const ERR_ALREADY: i32 = -11;
pub const ERR_IMPORT: i32 = -12;
