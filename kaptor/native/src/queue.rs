use std::sync::atomic::{AtomicU32, Ordering};

pub const HEADER_SIZE: usize = 32;
pub const SLOT_SIZE: usize = 8512;
pub const MAX_EVENT_TYPE: usize = 256;
pub const MAX_PAYLOAD: usize = 4096;
pub const MAX_RESULT: usize = 4096;

pub const MAGIC_EMPTY: u32 = 0;
pub const MAGIC_PENDING: u32 = 1;
pub const MAGIC_DONE: u32 = 2;
pub const MAGIC_ERROR: u32 = 3;

#[repr(C)]
pub struct Header {
    pub capacity: u64,
    pub slot_size: u64,
    pub version: u64,
    pub reserved: u64,
}

pub struct RingBuffer {
    pub base: usize,
    pub capacity: usize,
    pub slot_size: usize,
}

impl RingBuffer {
    pub fn from_address(addr: u64, size: usize) -> Self {
        let header = addr as *const Header;
        let header = unsafe { (*header).capacity };
        let capacity = if header > 0 {
            header as usize
        } else {
            (size.saturating_sub(HEADER_SIZE)) / SLOT_SIZE
        };
        let slot_size_raw = unsafe { *((addr as usize + 8) as *const u64) };
        let slot_size = if slot_size_raw > 0 {
            slot_size_raw as usize
        } else {
            SLOT_SIZE
        };
        RingBuffer {
            base: addr as usize,
            capacity,
            slot_size,
        }
    }

    #[inline]
    pub fn slot_addr(&self, index: usize) -> usize {
        self.base + HEADER_SIZE + index * self.slot_size
    }

    fn magic_ptr(slot: usize) -> *mut AtomicU32 {
        slot as *mut AtomicU32
    }

    #[inline]
    pub fn magic_load(slot: usize) -> u32 {
        unsafe { (*Self::magic_ptr(slot)).load(Ordering::Acquire) }
    }

    #[inline]
    pub fn magic_store(slot: usize, val: u32) {
        unsafe { (*Self::magic_ptr(slot)).store(val, Ordering::Release) }
    }

    #[inline]
    fn event_id_ptr(slot: usize) -> *mut u64 {
        unsafe { (slot as *mut u8).add(4) as *mut u64 }
    }

    #[inline]
    fn event_type_len_ptr(slot: usize) -> *mut u32 {
        unsafe { (slot as *mut u8).add(12) as *mut u32 }
    }

    #[inline]
    fn event_type_ptr(slot: usize) -> *mut u8 {
        unsafe { (slot as *mut u8).add(16) }
    }

    #[inline]
    fn payload_len_ptr(slot: usize) -> *mut u32 {
        unsafe { (slot as *mut u8).add(272) as *mut u32 }
    }

    #[inline]
    fn payload_ptr(slot: usize) -> *mut u8 {
        unsafe { (slot as *mut u8).add(276) }
    }

    #[inline]
    fn result_len_ptr(slot: usize) -> *mut u32 {
        unsafe { (slot as *mut u8).add(4372) as *mut u32 }
    }

    #[inline]
    fn result_ptr(slot: usize) -> *mut u8 {
        unsafe { (slot as *mut u8).add(4376) }
    }

    pub fn write_event(&self, slot_index: usize, event_id: u64, event_type: &str, payload: &str) {
        let slot = self.slot_addr(slot_index);
        unsafe {
            std::ptr::write(Self::event_id_ptr(slot), event_id);

            let type_bytes = event_type.as_bytes();
            let tl = type_bytes.len().min(MAX_EVENT_TYPE - 1) as u32;
            std::ptr::write(Self::event_type_len_ptr(slot), tl);
            std::ptr::copy_nonoverlapping(type_bytes.as_ptr(), Self::event_type_ptr(slot), tl as usize);
            Self::event_type_ptr(slot).add(tl as usize).write(0u8);

            let pb = payload.as_bytes();
            let pl = pb.len().min(MAX_PAYLOAD - 1) as u32;
            std::ptr::write(Self::payload_len_ptr(slot), pl);
            std::ptr::copy_nonoverlapping(pb.as_ptr(), Self::payload_ptr(slot), pl as usize);
            Self::payload_ptr(slot).add(pl as usize).write(0u8);

            std::ptr::write(Self::result_len_ptr(slot), 0u32);
        }
        Self::magic_store(slot, MAGIC_PENDING);
    }

    pub fn read_event(&self, slot_index: usize) -> (u64, String, String) {
        let slot = self.slot_addr(slot_index);
        unsafe {
            let id = std::ptr::read(Self::event_id_ptr(slot));
            let tl = std::ptr::read(Self::event_type_len_ptr(slot)) as usize;
            let et = std::str::from_utf8(std::slice::from_raw_parts(Self::event_type_ptr(slot), tl))
                .unwrap_or("").to_string();
            let pl = std::ptr::read(Self::payload_len_ptr(slot)) as usize;
            let pd = std::str::from_utf8(std::slice::from_raw_parts(Self::payload_ptr(slot), pl))
                .unwrap_or("").to_string();
            (id, et, pd)
        }
    }

    pub fn write_result(&self, slot_index: usize, result: &str, magic: u32) {
        let slot = self.slot_addr(slot_index);
        unsafe {
            let rb = result.as_bytes();
            let rl = rb.len().min(MAX_RESULT - 1) as u32;
            std::ptr::write(Self::result_len_ptr(slot), rl);
            std::ptr::copy_nonoverlapping(rb.as_ptr(), Self::result_ptr(slot), rl as usize);
            Self::result_ptr(slot).add(rl as usize).write(0u8);
        }
        Self::magic_store(slot, magic);
    }

    pub fn read_result(&self, slot_index: usize) -> (u32, String) {
        let slot = self.slot_addr(slot_index);
        let magic = Self::magic_load(slot);
        unsafe {
            let rl = std::ptr::read(Self::result_len_ptr(slot)) as usize;
            let rs = std::str::from_utf8(std::slice::from_raw_parts(Self::result_ptr(slot), rl))
                .unwrap_or("").to_string();
            (magic, rs)
        }
    }

    pub fn reset_slot(&self, slot_index: usize) {
        let slot = self.slot_addr(slot_index);
        unsafe {
            std::ptr::write(Self::event_id_ptr(slot), 0u64);
            std::ptr::write(Self::event_type_len_ptr(slot), 0u32);
            std::ptr::write(Self::payload_len_ptr(slot), 0u32);
            std::ptr::write(Self::result_len_ptr(slot), 0u32);
        }
        Self::magic_store(slot, MAGIC_EMPTY);
    }

    pub fn find_free_slot(&self) -> Option<usize> {
        for i in 0..self.capacity {
            let slot = self.slot_addr(i);
            if Self::magic_load(slot) == MAGIC_EMPTY {
                return Some(i);
            }
        }
        None
    }

    pub fn find_pending_slots(&self) -> Vec<usize> {
        let mut found = Vec::new();
        for i in 0..self.capacity {
            let slot = self.slot_addr(i);
            let magic = Self::magic_load(slot);
            if magic == MAGIC_PENDING {
                found.push(i);
            }
        }
        found
    }

    pub fn find_completed(&self, target_event_id: u64) -> Option<usize> {
        for i in 0..self.capacity {
            let slot = self.slot_addr(i);
            let magic = Self::magic_load(slot);
            if magic == MAGIC_DONE || magic == MAGIC_ERROR {
                unsafe {
                    let sid = std::ptr::read(Self::event_id_ptr(slot));
                    if sid == target_event_id {
                        return Some(i);
                    }
                }
            }
        }
        None
    }
}
