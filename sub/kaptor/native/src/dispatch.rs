use crate::hooks::HookPhase;

#[derive(Clone, Debug, Default)]
pub struct DispatchResult {
    pub payload: Vec<u8>,
    pub cancelled: bool,
    pub errors: Vec<String>,
    pub invoked: u32,
    pub fuel_used: u64,
    pub traps: u32,
}

impl DispatchResult {
    pub fn empty(payload: &[u8]) -> Self {
        DispatchResult {
            payload: payload.to_vec(),
            cancelled: false,
            errors: Vec::new(),
            invoked: 0,
            fuel_used: 0,
            traps: 0,
        }
    }

    #[allow(dead_code)]
    pub fn payload_str(&self) -> &str {
        std::str::from_utf8(&self.payload).unwrap_or("")
    }
}

pub fn should_skip(phase: HookPhase, cancelled: bool) -> bool {
    cancelled && phase != HookPhase::After
}
