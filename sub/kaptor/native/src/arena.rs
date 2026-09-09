use std::sync::atomic::{AtomicU32, Ordering};

pub const ARENA_MAGIC: u32 = 0x4B50_5452;
pub const ARENA_VERSION: u32 = 3;
pub const ARENA_HEADER_SIZE: usize = 64;
pub const SLOT_HDR_SIZE: usize = 32;

pub const DEFAULT_TYPE_CAP: usize = 256;
pub const DEFAULT_PAYLOAD_CAP: usize = 4096;
pub const DEFAULT_RESULT_CAP: usize = 4096;

pub const SLOT_EMPTY: u32 = 0;
pub const SLOT_PENDING: u32 = 1;
pub const SLOT_RUNNING: u32 = 2;
pub const SLOT_DONE: u32 = 3;
pub const SLOT_ERROR: u32 = 4;

pub const SLOT_FLAG_CANCELLED: u32 = 1;

pub const PHASE_ALL: u32 = 0;
pub const PHASE_BEFORE: u32 = 1;
pub const PHASE_ON: u32 = 2;
pub const PHASE_AFTER: u32 = 3;

pub const GUEST_MAILBOX_BASE: usize = 65536;

#[repr(C)]
#[derive(Clone, Copy)]
pub struct ArenaHeader {
    pub magic: u32,
    pub version: u32,
    pub header_size: u32,
    pub slot_size: u32,
    pub slot_count: u32,
    pub type_cap: u32,
    pub payload_cap: u32,
    pub result_cap: u32,
    pub flags: u32,
    pub reserved: [u32; 7],
}

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct Layout {
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

impl Layout {
    pub fn compute(type_cap: u32, payload_cap: u32, result_cap: u32, buf_size: usize) -> Self {
        let tc = if type_cap == 0 { DEFAULT_TYPE_CAP as u32 } else { type_cap };
        let pc = if payload_cap == 0 { DEFAULT_PAYLOAD_CAP as u32 } else { payload_cap };
        let rc = if result_cap == 0 { DEFAULT_RESULT_CAP as u32 } else { result_cap };
        let slot_size = (SLOT_HDR_SIZE as u32) + tc + pc + rc;
        let usable = buf_size.saturating_sub(ARENA_HEADER_SIZE);
        let count = (usable / slot_size as usize) as u32;
        Layout {
            header_size: ARENA_HEADER_SIZE as u32,
            slot_hdr_size: SLOT_HDR_SIZE as u32,
            slot_size,
            slot_count: count,
            type_off: SLOT_HDR_SIZE as u32,
            payload_off: SLOT_HDR_SIZE as u32 + tc,
            result_off: SLOT_HDR_SIZE as u32 + tc + pc,
            type_cap: tc,
            payload_cap: pc,
            result_cap: rc,
        }
    }

    pub fn mailbox_size(&self) -> usize {
        self.slot_size as usize
    }

    pub fn default_slot_size() -> usize {
        SLOT_HDR_SIZE + DEFAULT_TYPE_CAP + DEFAULT_PAYLOAD_CAP + DEFAULT_RESULT_CAP
    }
}

#[derive(Clone, Copy)]
pub struct Arena {
    pub base: usize,
    pub layout: Layout,
}

impl Arena {
    pub fn init(
        addr: u64,
        size: usize,
        type_cap: u32,
        payload_cap: u32,
        result_cap: u32,
    ) -> Result<Self, String> {
        if addr == 0 || size < ARENA_HEADER_SIZE + Layout::default_slot_size() {
            return Err("arena too small".into());
        }
        let layout = Layout::compute(type_cap, payload_cap, result_cap, size);
        if layout.slot_count == 0 {
            return Err("arena has no slots".into());
        }
        let header = ArenaHeader {
            magic: ARENA_MAGIC,
            version: ARENA_VERSION,
            header_size: ARENA_HEADER_SIZE as u32,
            slot_size: layout.slot_size,
            slot_count: layout.slot_count,
            type_cap: layout.type_cap,
            payload_cap: layout.payload_cap,
            result_cap: layout.result_cap,
            flags: 0,
            reserved: [0; 7],
        };
        unsafe {
            std::ptr::write(addr as *mut ArenaHeader, header);
            let slots = (addr as usize + ARENA_HEADER_SIZE) as *mut u8;
            std::ptr::write_bytes(slots, 0, layout.slot_count as usize * layout.slot_size as usize);
        }
        Ok(Arena {
            base: addr as usize,
            layout,
        })
    }

    pub fn attach(addr: u64, size: usize) -> Result<Self, String> {
        if addr == 0 || size < ARENA_HEADER_SIZE {
            return Err("invalid arena".into());
        }
        let header = unsafe { std::ptr::read(addr as *const ArenaHeader) };
        if header.magic != ARENA_MAGIC {
            return Err("bad arena magic".into());
        }
        if header.version != ARENA_VERSION {
            return Err("arena version mismatch".into());
        }
        let layout = Layout {
            header_size: header.header_size,
            slot_hdr_size: SLOT_HDR_SIZE as u32,
            slot_size: header.slot_size,
            slot_count: header.slot_count,
            type_off: SLOT_HDR_SIZE as u32,
            payload_off: SLOT_HDR_SIZE as u32 + header.type_cap,
            result_off: SLOT_HDR_SIZE as u32 + header.type_cap + header.payload_cap,
            type_cap: header.type_cap,
            payload_cap: header.payload_cap,
            result_cap: header.result_cap,
        };
        let need = ARENA_HEADER_SIZE + layout.slot_count as usize * layout.slot_size as usize;
        if size < need {
            return Err("arena buffer smaller than header".into());
        }
        Ok(Arena {
            base: addr as usize,
            layout,
        })
    }

    pub fn slot(&self, index: usize) -> Option<Slot> {
        if index >= self.layout.slot_count as usize {
            return None;
        }
        let ptr = self.base + ARENA_HEADER_SIZE + index * self.layout.slot_size as usize;
        Some(Slot {
            ptr,
            layout: self.layout,
        })
    }

    pub fn claim(&self) -> Option<usize> {
        for i in 0..self.layout.slot_count as usize {
            if let Some(s) = self.slot(i) {
                if s.cas_magic(SLOT_EMPTY, SLOT_PENDING) {
                    return Some(i);
                }
            }
        }
        None
    }

    pub fn pending(&self) -> Vec<usize> {
        let mut out = Vec::new();
        for i in 0..self.layout.slot_count as usize {
            if let Some(s) = self.slot(i) {
                if s.magic() == SLOT_PENDING {
                    out.push(i);
                }
            }
        }
        out
    }

    pub fn find_completed(&self, event_id: u64) -> Option<usize> {
        for i in 0..self.layout.slot_count as usize {
            if let Some(s) = self.slot(i) {
                let m = s.magic();
                if (m == SLOT_DONE || m == SLOT_ERROR) && s.event_id() == event_id {
                    return Some(i);
                }
            }
        }
        None
    }
}

#[derive(Clone, Copy)]
pub struct Slot {
    ptr: usize,
    pub layout: Layout,
}

impl Slot {
    fn atomic_magic(&self) -> &AtomicU32 {
        unsafe { &*(self.ptr as *const AtomicU32) }
    }

    pub fn magic(&self) -> u32 {
        self.atomic_magic().load(Ordering::Acquire)
    }

    pub fn set_magic(&self, v: u32) {
        self.atomic_magic().store(v, Ordering::Release);
    }

    pub fn cas_magic(&self, current: u32, new: u32) -> bool {
        self.atomic_magic()
            .compare_exchange(current, new, Ordering::AcqRel, Ordering::Acquire)
            .is_ok()
    }

    unsafe fn hdr_ptr(&self) -> *mut u8 {
        self.ptr as *mut u8
    }

    pub fn flags(&self) -> u32 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(4) as *const u32) }
    }

    pub fn set_flags(&self, v: u32) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(4) as *mut u32, v) }
    }

    pub fn event_id(&self) -> u64 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(8) as *const u64) }
    }

    pub fn set_event_id(&self, v: u64) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(8) as *mut u64, v) }
    }

    pub fn phase(&self) -> u32 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(16) as *const u32) }
    }

    pub fn set_phase(&self, v: u32) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(16) as *mut u32, v) }
    }

    pub fn type_len(&self) -> u32 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(20) as *const u32) }
    }

    pub fn payload_len(&self) -> u32 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(24) as *const u32) }
    }

    pub fn result_len(&self) -> u32 {
        unsafe { std::ptr::read_unaligned(self.hdr_ptr().add(28) as *const u32) }
    }

    fn set_type_len(&self, v: u32) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(20) as *mut u32, v) }
    }

    fn set_payload_len(&self, v: u32) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(24) as *mut u32, v) }
    }

    pub fn set_result_len(&self, v: u32) {
        unsafe { std::ptr::write_unaligned(self.hdr_ptr().add(28) as *mut u32, v) }
    }

    pub fn event_type(&self) -> &[u8] {
        let n = self.type_len() as usize;
        let n = n.min(self.layout.type_cap as usize);
        unsafe { std::slice::from_raw_parts(self.hdr_ptr().add(self.layout.type_off as usize), n) }
    }

    pub fn event_type_str(&self) -> &str {
        std::str::from_utf8(self.event_type()).unwrap_or("")
    }

    pub fn payload(&self) -> &[u8] {
        let n = self.payload_len() as usize;
        let n = n.min(self.layout.payload_cap as usize);
        unsafe { std::slice::from_raw_parts(self.hdr_ptr().add(self.layout.payload_off as usize), n) }
    }

    pub fn result(&self) -> &[u8] {
        let n = self.result_len() as usize;
        let n = n.min(self.layout.result_cap as usize);
        unsafe { std::slice::from_raw_parts(self.hdr_ptr().add(self.layout.result_off as usize), n) }
    }

    pub fn as_bytes(&self) -> &[u8] {
        unsafe { std::slice::from_raw_parts(self.hdr_ptr(), self.layout.slot_size as usize) }
    }

    pub fn write_event(&self, event_id: u64, phase: u32, ty: &str, payload: &[u8]) -> Result<(), String> {
        if ty.len() > self.layout.type_cap as usize {
            return Err("event type too long".into());
        }
        if payload.len() > self.layout.payload_cap as usize {
            return Err("payload too large".into());
        }
        let m = self.magic();
        if m == SLOT_RUNNING {
            return Err("slot busy".into());
        }
        self.set_event_id(event_id);
        self.set_phase(phase);
        self.set_flags(0);
        self.set_type_len(ty.len() as u32);
        self.set_payload_len(payload.len() as u32);
        self.set_result_len(0);
        unsafe {
            let tp = self.hdr_ptr().add(self.layout.type_off as usize);
            std::ptr::copy_nonoverlapping(ty.as_ptr(), tp, ty.len());
            let pp = self.hdr_ptr().add(self.layout.payload_off as usize);
            if !payload.is_empty() {
                std::ptr::copy_nonoverlapping(payload.as_ptr(), pp, payload.len());
            }
        }
        if m != SLOT_PENDING {
            self.set_magic(SLOT_PENDING);
        }
        Ok(())
    }

    pub fn write_result(&self, bytes: &[u8]) {
        let n = bytes.len().min(self.layout.result_cap as usize);
        unsafe {
            std::ptr::copy_nonoverlapping(
                bytes.as_ptr(),
                self.hdr_ptr().add(self.layout.result_off as usize),
                n,
            );
        }
        self.set_result_len(n as u32);
    }

    pub fn promote_result_to_payload(&self) {
        let n = self.result_len() as usize;
        if n == 0 {
            return;
        }
        let n = n.min(self.layout.payload_cap as usize);
        unsafe {
            std::ptr::copy(
                self.hdr_ptr().add(self.layout.result_off as usize),
                self.hdr_ptr().add(self.layout.payload_off as usize),
                n,
            );
        }
        self.set_payload_len(n as u32);
        self.set_result_len(0);
    }

    pub fn reset(&self) {
        self.set_event_id(0);
        self.set_phase(0);
        self.set_flags(0);
        self.set_type_len(0);
        self.set_payload_len(0);
        self.set_result_len(0);
        self.set_magic(SLOT_EMPTY);
    }

    pub fn output(&self) -> &[u8] {
        if self.result_len() > 0 {
            self.result()
        } else {
            self.payload()
        }
    }
}

pub fn scratch_arena(type_cap: u32, payload_cap: u32, result_cap: u32) -> (Vec<u8>, Arena) {
    let probe = Layout::compute(type_cap, payload_cap, result_cap, ARENA_HEADER_SIZE + 8);
    let size = ARENA_HEADER_SIZE + probe.slot_size as usize;
    let mut buf = vec![0u8; size];
    let addr = buf.as_mut_ptr() as u64;
    let arena = Arena::init(addr, size, type_cap, payload_cap, result_cap).expect("scratch");
    (buf, arena)
}
