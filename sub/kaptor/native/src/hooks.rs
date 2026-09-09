use std::sync::atomic::{AtomicI32, Ordering};

use serde::Deserialize;
use wasmtime::ExternType;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum HookPhase {
    Before,
    On,
    After,
}

impl HookPhase {
    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "before" | "BEFORE" => Some(HookPhase::Before),
            "on" | "ON" => Some(HookPhase::On),
            "after" | "AFTER" => Some(HookPhase::After),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            HookPhase::Before => "before",
            HookPhase::On => "on",
            HookPhase::After => "after",
        }
    }

    pub fn order(self) -> u8 {
        match self {
            HookPhase::Before => 0,
            HookPhase::On => 1,
            HookPhase::After => 2,
        }
    }
}

#[derive(Clone, Debug)]
pub struct Hook {
    pub id: i32,
    pub module_id: i32,
    pub phase: HookPhase,
    pub event_type: String,
    pub export_name: String,
}

pub struct HookRegistry {
    hooks: Vec<Hook>,
    next_id: AtomicI32,
}

impl HookRegistry {
    pub fn new() -> Self {
        HookRegistry {
            hooks: Vec::new(),
            next_id: AtomicI32::new(0),
        }
    }

    pub fn add(
        &mut self,
        module_id: i32,
        phase: HookPhase,
        event_type: &str,
        export_name: &str,
    ) -> i32 {
        if let Some(h) = self.hooks.iter().find(|h| {
            h.module_id == module_id
                && h.phase == phase
                && h.event_type == event_type
                && h.export_name == export_name
        }) {
            return h.id;
        }
        let id = self.next_id.fetch_add(1, Ordering::SeqCst);
        self.hooks.push(Hook {
            id,
            module_id,
            phase,
            event_type: event_type.to_string(),
            export_name: export_name.to_string(),
        });
        id
    }

    pub fn remove_id(&mut self, id: i32) -> bool {
        let before = self.hooks.len();
        self.hooks.retain(|h| h.id != id);
        self.hooks.len() != before
    }

    pub fn remove_module(&mut self, module_id: i32) {
        self.hooks.retain(|h| h.module_id != module_id);
    }

    pub fn get(&self, id: i32) -> Option<&Hook> {
        self.hooks.iter().find(|h| h.id == id)
    }

    pub fn ids(&self) -> Vec<i32> {
        self.hooks.iter().map(|h| h.id).collect()
    }

    pub fn all(&self) -> &[Hook] {
        &self.hooks
    }

    pub fn for_event(&self, event_type: &str) -> Vec<Hook> {
        let mut matched: Vec<Hook> = self
            .hooks
            .iter()
            .filter(|h| h.event_type == event_type)
            .cloned()
            .collect();
        matched.sort_by_key(|h| h.phase.order());
        matched
    }

    pub fn for_event_phase(&self, event_type: &str, phase: HookPhase) -> Vec<Hook> {
        self.hooks
            .iter()
            .filter(|h| h.event_type == event_type && h.phase == phase)
            .cloned()
            .collect()
    }
}

pub fn parse_export_name(name: &str) -> Option<(HookPhase, String)> {
    let (phase, rest) = if let Some(r) = name.strip_prefix("before_") {
        (HookPhase::Before, r)
    } else if let Some(r) = name.strip_prefix("after_") {
        (HookPhase::After, r)
    } else if let Some(r) = name.strip_prefix("on_") {
        (HookPhase::On, r)
    } else {
        return None;
    };
    if rest.is_empty() {
        return None;
    }
    Some((phase, rest.replace('$', ".")))
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum HookAbi {
    Slot,
    Legacy,
}

pub fn classify_hook(ty: &ExternType) -> Option<HookAbi> {
    let ExternType::Func(ft) = ty else {
        return None;
    };
    let params: Vec<_> = ft.params().collect();
    let results: Vec<_> = ft.results().collect();
    if results.len() != 1 || !results[0].is_i32() || !params.iter().all(|t| t.is_i32()) {
        return None;
    }
    match params.len() {
        1 => Some(HookAbi::Slot),
        6 => Some(HookAbi::Legacy),
        _ => None,
    }
}

pub fn is_hook_signature(ty: &ExternType) -> bool {
    classify_hook(ty).is_some()
}

#[derive(Deserialize)]
struct Manifest {
    hooks: Vec<ManifestHook>,
}

#[derive(Deserialize)]
struct ManifestHook {
    phase: String,
    event: String,
    export: String,
}

pub fn parse_manifest(json: &str) -> Vec<(HookPhase, String, String)> {
    let parsed: Manifest = match serde_json::from_str(json) {
        Ok(m) => m,
        Err(_) => return Vec::new(),
    };
    parsed
        .hooks
        .into_iter()
        .filter_map(|h| {
            let phase = HookPhase::parse(&h.phase)?;
            if h.event.is_empty() || h.export.is_empty() {
                return None;
            }
            Some((phase, h.event, h.export))
        })
        .collect()
}
