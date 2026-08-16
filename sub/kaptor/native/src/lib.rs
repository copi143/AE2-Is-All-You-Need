mod queue;
mod runtime;

use std::ffi::CStr;
use std::os::raw::c_char;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use queue::{Header, RingBuffer, SLOT_SIZE, HEADER_SIZE, MAGIC_DONE, MAGIC_ERROR, MAGIC_EMPTY};
use runtime::WasmtimeRuntime;

static mut GLOBAL: Option<Arc<WasmtimeRuntime>> = None;
static RUNNING: AtomicBool = AtomicBool::new(false);
static WORKER: std::sync::Mutex<Option<thread::JoinHandle<()>>> = std::sync::Mutex::new(None);

static mut BUFFER_ADDR: u64 = 0;
static mut BUFFER_SIZE: usize = 0;

const HOOK_TYPES_ALL: &[&str] = &["before", "on", "after"];

unsafe fn buffer() -> RingBuffer {
    RingBuffer::from_address(BUFFER_ADDR, BUFFER_SIZE)
}

fn worker_loop(rt: Arc<WasmtimeRuntime>) {
    let rb = unsafe { buffer() };

    while RUNNING.load(Ordering::SeqCst) {
        let pending = rb.find_pending_slots();
        if pending.is_empty() {
            thread::sleep(Duration::from_micros(50));
            continue;
        }

        for idx in pending {
            let (_event_id, event_type, payload) = rb.read_event(idx);
            let result = rt.dispatch_event(&event_type, HOOK_TYPES_ALL, &payload);
            match &result {
                Ok(s) => rb.write_result(idx, s, MAGIC_DONE),
                Err(_) => rb.write_result(idx, "", MAGIC_ERROR),
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn kaptor_init(buf_addr: u64, buf_size: usize) -> i32 {
    if RUNNING.load(Ordering::SeqCst) {
        return -1;
    }

    // Write header into the shared buffer so both sides agree on layout
    let capacity = (buf_size.saturating_sub(HEADER_SIZE)) / SLOT_SIZE;
    let header = Header {
        capacity: capacity as u64,
        slot_size: SLOT_SIZE as u64,
        version: 1,
        reserved: 0,
    };
    unsafe {
        std::ptr::write(buf_addr as *mut Header, header);
        BUFFER_ADDR = buf_addr;
        BUFFER_SIZE = buf_size;
    }

    let runtime = match WasmtimeRuntime::new() {
        Ok(r) => r,
        Err(e) => {
            eprintln!("kaptor: runtime init failed: {}", e);
            return -2;
        }
    };

    let rt = Arc::new(runtime);
    RUNNING.store(true, Ordering::SeqCst);

    let rt_clone = Arc::clone(&rt);
    let handle = thread::spawn(move || {
        worker_loop(rt_clone);
    });

    *WORKER.lock().unwrap() = Some(handle);
    unsafe { GLOBAL = Some(rt); }
    0
}

#[no_mangle]
pub extern "C" fn kaptor_shutdown() -> i32 {
    RUNNING.store(false, Ordering::SeqCst);
    let handle = WORKER.lock().unwrap().take();
    if let Some(h) = handle {
        let _ = h.join();
    }
    unsafe { GLOBAL = None; }
    0
}

#[no_mangle]
pub extern "C" fn kaptor_load_module(
    name: *const c_char,
    wasm_bytes: *const u8,
    len: usize,
) -> i32 {
    if wasm_bytes.is_null() || len == 0 {
        return -1;
    }
    let rt = unsafe { GLOBAL.as_ref() };
    let rt = match rt {
        Some(r) => r,
        None => return -2,
    };
    let name_str = if name.is_null() {
        ""
    } else {
        unsafe { CStr::from_ptr(name) }.to_str().unwrap_or("")
    };
    let bytes = unsafe { std::slice::from_raw_parts(wasm_bytes, len) };
    match rt.load_module(name_str, bytes) {
        Ok(id) => id,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_register_hook(
    module_id: i32,
    hook_type: *const c_char,
    event_type: *const c_char,
    export_name: *const c_char,
) -> i32 {
    let rt = unsafe { GLOBAL.as_ref() };
    let rt = match rt {
        Some(r) => r,
        None => return -2,
    };
    let ht = if hook_type.is_null() { "" } else { unsafe { CStr::from_ptr(hook_type) }.to_str().unwrap_or("") };
    let et = if event_type.is_null() { "" } else { unsafe { CStr::from_ptr(event_type) }.to_str().unwrap_or("") };
    let en = if export_name.is_null() { "" } else { unsafe { CStr::from_ptr(export_name) }.to_str().unwrap_or("") };
    match rt.register_hook(module_id, ht, et, en) {
        Ok(id) => id,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_submit_event(
    event_type: *const c_char,
    json_data: *const c_char,
    event_id: u64,
) -> i32 {
    let running = RUNNING.load(Ordering::SeqCst);
    if !running {
        return -2;
    }
    let et = if event_type.is_null() { "" } else { unsafe { CStr::from_ptr(event_type) }.to_str().unwrap_or("") };
    let jd = if json_data.is_null() { "" } else { unsafe { CStr::from_ptr(json_data) }.to_str().unwrap_or("") };

    let rb = unsafe { buffer() };
    match rb.find_free_slot() {
        Some(idx) => {
            rb.write_event(idx, event_id, et, jd);
            idx as i32
        }
        None => -1,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_wait_result(event_id: u64, timeout_ms: u32) -> i32 {
    let rb = unsafe { buffer() };
    let deadline = std::time::Instant::now() + Duration::from_millis(timeout_ms as u64);
    loop {
        if let Some(idx) = rb.find_completed(event_id) {
            return idx as i32;
        }
        if std::time::Instant::now() > deadline {
            return -1;
        }
        thread::sleep(Duration::from_micros(100));
    }
}

#[no_mangle]
pub extern "C" fn kaptor_read_result(slot_index: i32, buf: *mut u8, buf_size: usize) -> i32 {
    if slot_index < 0 || buf.is_null() || buf_size == 0 {
        return -1;
    }
    let rb = unsafe { buffer() };
    let (_magic, result_str) = rb.read_result(slot_index as usize);
    let bytes = result_str.as_bytes();
    let copy_len = bytes.len().min(buf_size - 1);
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), buf, copy_len);
        buf.add(copy_len).write(0u8);
    }
    rb.reset_slot(slot_index as usize);
    copy_len as i32
}

#[no_mangle]
pub extern "C" fn kaptor_dispatch_sync(
    event_type: *const c_char,
    json_data: *const c_char,
    result_buf: *mut u8,
    result_buf_size: usize,
) -> i32 {
    if result_buf.is_null() || result_buf_size == 0 {
        return -1;
    }
    let rt = unsafe { GLOBAL.as_ref() };
    let rt = match rt {
        Some(r) => r,
        None => return -2,
    };
    let et = if event_type.is_null() { "" } else { unsafe { CStr::from_ptr(event_type) }.to_str().unwrap_or("") };
    let jd = if json_data.is_null() { "" } else { unsafe { CStr::from_ptr(json_data) }.to_str().unwrap_or("") };
    let result = rt.dispatch_event(et, HOOK_TYPES_ALL, jd);
    match result {
        Ok(s) => {
            let bytes = s.as_bytes();
            let copy_len = bytes.len().min(result_buf_size - 1);
            unsafe {
                std::ptr::copy_nonoverlapping(bytes.as_ptr(), result_buf, copy_len);
                result_buf.add(copy_len).write(0u8);
            }
            copy_len as i32
        }
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn kaptor_slot_size() -> usize {
    SLOT_SIZE
}

#[no_mangle]
pub extern "C" fn kaptor_header_size() -> usize {
    HEADER_SIZE
}

#[no_mangle]
pub extern "C" fn kaptor_magic_done() -> u32 {
    MAGIC_DONE
}

#[no_mangle]
pub extern "C" fn kaptor_magic_error() -> u32 {
    MAGIC_ERROR
}

#[no_mangle]
pub extern "C" fn kaptor_magic_pending() -> u32 {
    queue::MAGIC_PENDING
}

#[no_mangle]
pub extern "C" fn kaptor_magic_empty() -> u32 {
    MAGIC_EMPTY
}
