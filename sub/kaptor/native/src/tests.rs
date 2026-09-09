use crate::arena::{Arena, SLOT_DONE, SLOT_FLAG_CANCELLED};
use crate::config::EngineConfig;
use crate::dispatch::should_skip;
use crate::hooks::HookPhase;
use crate::runtime::KaptorRuntime;

fn wat(src: &str) -> Vec<u8> {
    wat::parse_str(src).expect("wat")
}

fn echo_module(export: &str) -> Vec<u8> {
    wat(&format!(
        r#"
        (module
          (memory (export "memory") 1)
          (func (export "{export}") (param i32 i32 i32 i32 i32 i32) (result i32)
            (local $i i32)
            (local.set $i (i32.const 0))
            (loop $copy
              (if (i32.lt_u (local.get $i) (local.get 3))
                (then
                  (i32.store8
                    (i32.add (local.get 4) (local.get $i))
                    (i32.load8_u (i32.add (local.get 2) (local.get $i))))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $copy))))
            (local.get 3)))
        "#
    ))
}

fn tag_module(export: &str, tag: &str) -> Vec<u8> {
    let stores = tag
        .bytes()
        .enumerate()
        .map(|(i, b)| {
            format!(
                "(i32.store8 (i32.add (local.get 4) (i32.add (local.get 3) (i32.const {i}))) (i32.const {b}))"
            )
        })
        .collect::<Vec<_>>()
        .join("\n            ");
    wat(&format!(
        r#"
        (module
          (memory (export "memory") 1)
          (func (export "{export}") (param i32 i32 i32 i32 i32 i32) (result i32)
            (local $i i32)
            (local.set $i (i32.const 0))
            (loop $copy
              (if (i32.lt_u (local.get $i) (local.get 3))
                (then
                  (i32.store8
                    (i32.add (local.get 4) (local.get $i))
                    (i32.load8_u (i32.add (local.get 2) (local.get $i))))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $copy))))
            {stores}
            (i32.add (local.get 3) (i32.const {n}))))
        "#,
        n = tag.len(),
    ))
}

fn cancel_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (import "kaptor" "cancel" (func $cancel))
          (memory (export "memory") 1)
          (func (export "before_Ping") (param i32 i32 i32 i32 i32 i32) (result i32)
            (call $cancel)
            (i32.const 0)))
        "#,
    )
}

fn loop_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (memory (export "memory") 1)
          (func (export "on_Loop") (param i32 i32 i32 i32 i32 i32) (result i32)
            (loop $l (br $l))
            (i32.const 0)))
        "#,
    )
}

fn manifest_module() -> Vec<u8> {
    let json = r#"{"hooks":[{"phase":"on","event":"Custom.Event","export":"handle"}]}"#;
    let escaped = json.replace('\\', "\\\\").replace('"', "\\\"");
    wat(&format!(
        r#"
        (module
          (memory (export "memory") 1)
          (data (i32.const 8000) "{escaped}")
          (func (export "kaptor_manifest") (result i32 i32)
            (i32.const 8000)
            (i32.const {len}))
          (func (export "handle") (param i32 i32 i32 i32 i32 i32) (result i32)
            (i32.store8 (local.get 4) (i32.const 79))
            (i32.const 1)))
        "#,
        len = json.len(),
    ))
}

#[test]
fn load_discover_dispatch() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    assert!(id >= 0);
    let hooks = rt.hooks_snapshot();
    assert_eq!(hooks.len(), 1);
    assert_eq!(hooks[0].event_type, "Ping");
    assert_eq!(hooks[0].phase, HookPhase::On);
    let out = rt.dispatch("Ping", "{\"n\":1}");
    assert_eq!(out.payload_str(), "{\"n\":1}");
    assert_eq!(out.invoked, 1);
    assert!(out.errors.is_empty());
}

#[test]
fn dollar_in_export_becomes_dot() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("x", &echo_module("on_Player$Join")).unwrap();
    let hooks = rt.hooks_snapshot();
    assert_eq!(hooks[0].event_type, "Player.Join");
}

#[test]
fn pipeline_mutates_payload() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("a", &tag_module("before_E", "+b")).unwrap();
    rt.load_module("b", &tag_module("on_E", "+o")).unwrap();
    rt.load_module("c", &tag_module("after_E", "+a")).unwrap();
    let out = rt.dispatch("E", "x");
    assert_eq!(out.payload_str(), "x+b+o+a");
    assert_eq!(out.invoked, 3);
}

#[test]
fn cancel_skips_on_runs_after() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("c", &cancel_module()).unwrap();
    rt.load_module("o", &tag_module("on_Ping", "ON")).unwrap();
    rt.load_module("a", &tag_module("after_Ping", "AF")).unwrap();
    let out = rt.dispatch("Ping", "z");
    assert!(out.cancelled);
    assert!(rt.was_cancelled());
    assert_eq!(out.payload_str(), "zAF");
    assert_eq!(out.invoked, 2);
}

#[test]
fn unload_removes_hooks() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    rt.unload_module(id).unwrap();
    assert!(rt.hooks_snapshot().is_empty());
    let out = rt.dispatch("Ping", "x");
    assert_eq!(out.invoked, 0);
    assert_eq!(out.payload_str(), "x");
}

#[test]
fn reload_same_name_replaces() {
    let rt = KaptorRuntime::new().unwrap();
    let id1 = rt.load_module("m", &tag_module("on_E", "A")).unwrap();
    let id2 = rt.load_module("m", &tag_module("on_E", "B")).unwrap();
    assert_eq!(id1, id2);
    let out = rt.dispatch("E", "");
    assert_eq!(out.payload_str(), "B");
}

#[test]
fn explicit_register_and_phase() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt
        .load_module("m", &echo_module("handle"))
        .unwrap();
    assert!(rt.hooks_snapshot().is_empty());
    rt.register_hook(id, "on", "Tick", "handle").unwrap();
    let out = rt.dispatch_phase("Tick", "on", "hi");
    assert_eq!(out.payload_str(), "hi");
    rt.unregister_hook(rt.hooks_snapshot()[0].id).unwrap();
    let out = rt.dispatch("Tick", "hi");
    assert_eq!(out.invoked, 0);
}

#[test]
fn fuel_traps_infinite_loop() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt.load_module("loop", &loop_module()).unwrap();
    rt.set_fuel(id, 1000).unwrap();
    let out = rt.dispatch("Loop", "");
    assert_eq!(out.invoked, 0);
    assert!(!out.errors.is_empty());
}

#[test]
fn manifest_discovery() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("man", &manifest_module()).unwrap();
    let hooks = rt.hooks_snapshot();
    assert_eq!(hooks.len(), 1);
    assert_eq!(hooks[0].event_type, "Custom.Event");
    assert_eq!(hooks[0].export_name, "handle");
    let out = rt.dispatch("Custom.Event", "");
    assert_eq!(out.payload_str(), "O");
}

#[test]
fn dump_registry_json() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    let dump = rt.dump_registry();
    assert!(dump.contains("\"name\":\"echo\""));
    assert!(dump.contains("\"event\":\"Ping\""));
}

#[test]
fn skip_helper() {
    assert!(should_skip(HookPhase::On, true));
    assert!(should_skip(HookPhase::Before, true));
    assert!(!should_skip(HookPhase::After, true));
    assert!(!should_skip(HookPhase::On, false));
}

fn wasi_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (import "wasi_snapshot_preview1" "proc_exit" (func (param i32)))
          (memory (export "memory") 1))
        "#,
    )
}

fn alloc_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (memory (export "memory") 1)
          (global $bump (mut i32) (i32.const 10000))
          (func (export "kaptor_alloc") (param i32) (result i32)
            (local $p i32)
            (local.set $p (global.get $bump))
            (global.set $bump (i32.add (global.get $bump) (local.get 0)))
            (local.get $p))
          (func (export "on_Ping") (param i32 i32 i32 i32 i32 i32) (result i32)
            (local $i i32)
            (local.set $i (i32.const 0))
            (loop $copy
              (if (i32.lt_u (local.get $i) (local.get 3))
                (then
                  (i32.store8
                    (i32.add (local.get 4) (local.get $i))
                    (i32.load8_u (i32.add (local.get 2) (local.get $i))))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $copy))))
            (local.get 3)))
        "#,
    )
}

fn log_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (import "kaptor" "log" (func $log (param i32 i32 i32)))
          (memory (export "memory") 1)
          (data (i32.const 9000) "hi")
          (func (export "on_Ping") (param i32 i32 i32 i32 i32 i32) (result i32)
            (call $log (i32.const 1) (i32.const 9000) (i32.const 2))
            (i32.const 0)))
        "#,
    )
}

#[test]
fn rejects_forbidden_import() {
    let rt = KaptorRuntime::new().unwrap();
    let err = rt.load_module("bad", &wasi_module()).unwrap_err();
    assert!(err.contains("forbidden import"));
}

#[test]
fn guest_alloc_large_payload() {
    let mut cfg = EngineConfig::default();
    cfg.payload_cap = 8192;
    cfg.result_cap = 8192;
    cfg.start_epoch = false;
    let rt = KaptorRuntime::with_config(cfg).unwrap();
    rt.load_module("alloc", &alloc_module()).unwrap();
    let payload = "x".repeat(5000);
    let out = rt.dispatch("Ping", &payload);
    assert!(out.errors.is_empty(), "{:?}", out.errors);
    assert_eq!(out.payload_str(), payload);
}

#[test]
fn stats_and_logs() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("log", &log_module()).unwrap();
    rt.dispatch("Ping", "");
    let stats = rt.stats();
    assert_eq!(stats.invokes, 1);
    assert_eq!(stats.modules, 1);
    assert_eq!(stats.hooks, 1);
    let logs = rt.drain_logs();
    assert_eq!(logs, vec![(1, "hi".into())]);
    assert!(rt.drain_logs().is_empty());
}

#[test]
fn module_and_hook_info() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    let info = rt.module_info(id).unwrap();
    let name = std::ffi::CStr::from_bytes_until_nul(&info.name)
        .unwrap()
        .to_str()
        .unwrap();
    assert_eq!(name, "echo");
    let hid = rt.hook_ids()[0];
    let h = rt.hook_info(hid).unwrap();
    let event = std::ffi::CStr::from_bytes_until_nul(&h.event)
        .unwrap()
        .to_str()
        .unwrap();
    assert_eq!(event, "Ping");
}

#[test]
fn register_rejects_missing_export() {
    let rt = KaptorRuntime::new().unwrap();
    let id = rt.load_module("m", &echo_module("handle")).unwrap();
    assert!(rt.register_hook(id, "on", "Tick", "nope").is_err());
}

fn slot_echo_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (memory (export "memory") 1)
          (func (export "on_Ping") (param $s i32) (result i32)
            (local $tcap i32) (local $pcap i32) (local $plen i32) (local $i i32)
            (local $src i32) (local $dst i32)
            (local.set $tcap (i32.load (local.get $s)))
            (local.set $pcap (i32.load (i32.add (local.get $s) (i32.const 8))))
            (local.set $plen (i32.load (i32.add (local.get $s) (i32.const 24))))
            (local.set $src (i32.add (i32.add (local.get $s) (i32.const 32)) (local.get $tcap)))
            (local.set $dst (i32.add (local.get $src) (local.get $pcap)))
            (local.set $i (i32.const 0))
            (loop $c
              (if (i32.lt_u (local.get $i) (local.get $plen))
                (then
                  (i32.store8 (i32.add (local.get $dst) (local.get $i))
                              (i32.load8_u (i32.add (local.get $src) (local.get $i))))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $c))))
            (i32.store (i32.add (local.get $s) (i32.const 28)) (local.get $plen))
            (i32.const 0)))
        "#,
    )
}

fn slot_cancel_module() -> Vec<u8> {
    wat(
        r#"
        (module
          (memory (export "memory") 1)
          (func (export "before_Ping") (param $s i32) (result i32)
            (i32.store (i32.add (local.get $s) (i32.const 4)) (i32.const 1))
            (i32.const 0)))
        "#,
    )
}

#[test]
fn slot_abi_echo() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("s", &slot_echo_module()).unwrap();
    let out = rt.dispatch("Ping", "abc");
    assert!(out.errors.is_empty(), "{:?}", out.errors);
    assert_eq!(out.payload_str(), "abc");
}

#[test]
fn slot_abi_cancel_via_mailbox_flags() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("c", &slot_cancel_module()).unwrap();
    rt.load_module("o", &echo_module("on_Ping")).unwrap();
    let out = rt.dispatch("Ping", "z");
    assert!(out.cancelled);
    assert_eq!(out.payload_str(), "z");
    assert_eq!(out.invoked, 1);
}

#[test]
fn shared_arena_kick() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    let mut buf = vec![0u8; 64 + 8480 * 2];
    let addr = buf.as_mut_ptr() as u64;
    let arena = Arena::init(addr, buf.len(), 256, 4096, 4096).unwrap();
    let idx = arena.claim().unwrap();
    let slot = arena.slot(idx).unwrap();
    slot.write_event(7, 0, "Ping", b"hello").unwrap();
    let out = rt.process_slot(&slot);
    assert!(out.errors.is_empty(), "{:?}", out.errors);
    assert_eq!(slot.magic(), SLOT_DONE);
    assert_eq!(slot.output(), b"hello");
    assert_eq!(slot.event_id(), 7);
}

#[test]
fn slot_cancel_flag_in_arena() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("c", &slot_cancel_module()).unwrap();
    let mut buf = vec![0u8; 64 + 8480];
    let addr = buf.as_mut_ptr() as u64;
    let arena = Arena::init(addr, buf.len(), 0, 0, 0).unwrap();
    let slot = arena.slot(arena.claim().unwrap()).unwrap();
    slot.write_event(1, 0, "Ping", b"x").unwrap();
    rt.process_slot(&slot);
    assert_eq!(slot.flags() & SLOT_FLAG_CANCELLED, SLOT_FLAG_CANCELLED);
}

#[test]
fn process_slot_requires_pending() {
    let rt = KaptorRuntime::new().unwrap();
    rt.load_module("echo", &echo_module("on_Ping")).unwrap();
    let mut buf = vec![0u8; 64 + 8480];
    let arena = Arena::init(buf.as_mut_ptr() as u64, buf.len(), 0, 0, 0).unwrap();
    let slot = arena.slot(0).unwrap();
    let out = rt.process_slot(&slot);
    assert!(out.errors.iter().any(|e| e.contains("not pending")));
    slot.write_event(1, 0, "Ping", b"x").unwrap();
    let out = rt.process_slot(&slot);
    assert!(out.errors.is_empty(), "{:?}", out.errors);
    let out = rt.process_slot(&slot);
    assert!(out.errors.iter().any(|e| e.contains("not pending")));
}
