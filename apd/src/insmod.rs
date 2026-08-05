//! Load a kernel module (.ko) without the kernel's strict version/symbol check.
//!
//! Undefined symbols of the module are resolved against `/proc/kallsyms` and the
//! module is then loaded via the `init_module(2)` syscall. This is a direct port
//! of KernelSU's `ksuinit::load_module`, letting a prebuilt `kernelpatch.ko` be
//! loaded on a stock kernel for jailbreak mode. The manual relocation bypasses
//! both the per-symbol modversions (CRC) check and the vermagic check (the kernel
//! skips the vermagic comparison when the module carries a `__versions` section).

use anyhow::{Context, Result};
use goblin::elf::{Elf, section_header, sym::Sym};
use scroll::{Pwrite, ctx::SizeWith};
use std::collections::HashMap;
use std::ffi::{CStr, CString};
use std::fs;
use std::io::{BufRead, BufReader};
use std::path::Path;

/// Temporarily set `kptr_restrict` to 1 so `/proc/kallsyms` exposes addresses.
struct KptrRestrict(String);

impl KptrRestrict {
    fn new() -> Result<Self> {
        let path = "/proc/sys/kernel/kptr_restrict";
        let value = fs::read_to_string(path).context("read kptr_restrict failed")?;
        fs::write(path, "1").context("write kptr_restrict failed")?;
        Ok(KptrRestrict(value))
    }
}

impl Drop for KptrRestrict {
    fn drop(&mut self) {
        let _ = fs::write("/proc/sys/kernel/kptr_restrict", self.0.as_bytes());
    }
}

fn kernel_symbols() -> Result<Vec<(String, u64)>> {
    let _kptr = KptrRestrict::new()?;
    let file =
        BufReader::new(fs::File::open("/proc/kallsyms").context("open /proc/kallsyms failed")?);

    let mut symbols = Vec::new();
    for line in file.lines() {
        let line = line?;
        let mut splits = line.split_whitespace();
        let Some(addr) = splits.next().and_then(|a| u64::from_str_radix(a, 16).ok()) else {
            continue;
        };
        let _ty = splits.next();
        let Some(symbol) = splits.next() else { continue };
        // Stop reading as soon as we hit module symbols.
        if splits.next().is_some() {
            break;
        }
        let symbol = symbol
            .find('$')
            .or_else(|| symbol.find(".llvm."))
            .map(|pos| &symbol[..pos])
            .unwrap_or(symbol);
        symbols.push((symbol.to_owned(), addr));
    }
    Ok(symbols)
}

/// Relocate undefined symbols in an ELF module buffer with `/proc/kallsyms`, then
/// load it via the `init_module` syscall. Ported from KernelSU's `ksuinit`.
pub fn load_module(data: &[u8], params: &CStr) -> Result<()> {
    let mut buffer = data.to_vec();
    let elf = Elf::parse(&buffer).context("parse ELF failed")?;
    let ctx = *elf.syms.ctx();

    let mut unresolved: HashMap<String, (Sym, usize)> = HashMap::new();
    for (index, sym) in elf.syms.iter().enumerate() {
        if index == 0 {
            continue;
        }
        if sym.st_shndx != section_header::SHN_UNDEF as usize {
            continue;
        }
        let Some(name) = elf.strtab.get_at(sym.st_name) else {
            continue;
        };
        let offset = elf.syms.offset() + index * Sym::size_with(elf.syms.ctx());
        unresolved.insert(name.to_owned(), (sym, offset));
    }

    if !unresolved.is_empty() {
        for (symbol, addr) in kernel_symbols()? {
            if let Some((mut sym, offset)) = unresolved.remove(&symbol) {
                sym.st_shndx = section_header::SHN_ABS as usize;
                sym.st_value = addr;
                buffer.pwrite_with(sym, offset, ctx)?;
            }
            if unresolved.is_empty() {
                break;
            }
        }
    }

    for name in unresolved.keys() {
        log::warn!("cannot find kernel symbol: {name}");
    }

    let ret = unsafe {
        libc::syscall(
            libc::SYS_init_module,
            buffer.as_ptr(),
            buffer.len(),
            params.as_ptr(),
        )
    };
    if ret != 0 {
        return Err(std::io::Error::last_os_error()).context("init_module failed");
    }
    Ok(())
}

/// `apd insmod <module> [params...]` - load a kernel module without version check.
pub fn insmod(module: &Path, params: &[String]) -> Result<()> {
    let module = module
        .canonicalize()
        .with_context(|| format!("resolve module path failed: {}", module.display()))?;
    let module_data =
        fs::read(&module).with_context(|| format!("read module failed: {}", module.display()))?;
    let cparams = CString::new(params.join(" ")).context("invalid module parameters")?;
    load_module(&module_data, &cparams)
        .with_context(|| format!("load module failed: {}", module.display()))?;
    println!("Loaded kernel module: {}", module.display());
    Ok(())
}
