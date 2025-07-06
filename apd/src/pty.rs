use std::ffi::c_int;
use std::fs::File;
use std::io::{Read, Write, stderr, stdin, stdout};
use std::mem::MaybeUninit;
use std::os::fd::{AsFd, AsRawFd, OwnedFd, RawFd};
use std::process::exit;
use std::ptr::null_mut;
use std::thread;

use crate::defs::PTS_NAME;
use crate::utils::get_tmp_path;
use anyhow::{Ok, Result, bail};
use libc::{
    __errno, EINTR, SIG_BLOCK, SIG_UNBLOCK, SIGWINCH, TIOCGWINSZ, TIOCSWINSZ, fork,
    pthread_sigmask, sigaddset, sigemptyset, sigset_t, sigwait, waitpid, winsize,
};
use rustix::fs::{Mode, OFlags, open};
use rustix::io::dup;
use rustix::ioctl::{Getter, ReadOpcode, ioctl};
use rustix::process::setsid;
use rustix::pty::{grantpt, unlockpt};
use rustix::stdio::{dup2_stderr, dup2_stdin, dup2_stdout};
use rustix::termios::{OptionalActions, Termios, isatty, tcgetattr, tcsetattr};
use std::sync::Mutex;

// https://github.com/topjohnwu/Magisk/blob/5627053b7481618adfdf8fa3569b48275589915b/native/src/core/su/pts.cpp

fn get_pty_num<F: AsFd>(fd: F) -> Result<u32> {
    Ok(unsafe {
        let tiocgptn = Getter::<ReadOpcode<b'T', 0x30, u32>, u32>::new();
        ioctl(fd, tiocgptn)?
    })
}

static OLD_STDIN: Mutex<Option<Termios>> = Mutex::new(None);

fn watch_sigwinch_async(slave: RawFd) {
    let mut winch = MaybeUninit::<sigset_t>::uninit();
    unsafe {
        sigemptyset(winch.as_mut_ptr());
        sigaddset(winch.as_mut_ptr(), SIGWINCH);
        pthread_sigmask(SIG_BLOCK, winch.as_mut_ptr(), null_mut());
    }

    thread::spawn(move || unsafe {
        let mut winch = MaybeUninit::<sigset_t>::uninit();
        sigemptyset(winch.as_mut_ptr());
        sigaddset(winch.as_mut_ptr(), SIGWINCH);
        pthread_sigmask(SIG_UNBLOCK, winch.as_mut_ptr(), null_mut());
        let mut sig: c_int = 0;
        loop {
            let mut w = MaybeUninit::<winsize>::uninit();
            if libc::ioctl(1, TIOCGWINSZ, w.as_mut_ptr()) < 0 {
                continue;
            }
            libc::ioctl(slave, TIOCSWINSZ, w.as_mut_ptr());
            if sigwait(winch.as_mut_ptr(), &mut sig) != 0 {
                break;
            }
        }
    });
}

fn set_stdin_raw() -> rustix::io::Result<()> {
    let mut termios = tcgetattr(stdin())?;

    let mut guard = OLD_STDIN.lock().unwrap();
    *guard = Some(termios.clone());
    drop(guard);

    termios.make_raw();
    tcsetattr(stdin(), OptionalActions::Flush, &termios)
}

fn restore_stdin() -> Result<()> {
    let mut guard = OLD_STDIN.lock().unwrap();

    if let Some(original_termios) = guard.take() {
        tcsetattr(stdin(), OptionalActions::Flush, &original_termios)?;
    }

    Ok(())
}

fn pump<R: Read, W: Write>(mut from: R, mut to: W) {
    let mut buf = [0u8; 4096];
    loop {
        match from.read(&mut buf) {
            Result::Ok(len) => {
                if len == 0 {
                    return;
                }
                if to.write_all(&buf[0..len]).is_err() {
                    return;
                }
                if to.flush().is_err() {
                    return;
                }
            }
            Err(_) => {
                return;
            }
        }
    }
}

fn pump_stdin_async(mut ptmx: File) {
    let _ = set_stdin_raw();

    thread::spawn(move || {
        let mut stdin = stdin();
        pump(&mut stdin, &mut ptmx);
    });
}

fn pump_stdout_blocking(mut ptmx: File) {
    let mut stdout = stdout();
    pump(&mut ptmx, &mut stdout);

    let _ = restore_stdin();
}

fn create_transfer(ptmx: OwnedFd) -> Result<()> {
    let pid = unsafe { fork() };
    match pid {
        d if d < 0 => bail!("fork"),
        0 => return Ok(()),
        _ => {}
    }

    let ptmx_r = ptmx;
    let ptmx_w = dup(&ptmx_r)?;

    let ptmx_r = File::from(ptmx_r);
    let ptmx_w = File::from(ptmx_w);

    watch_sigwinch_async(ptmx_w.as_raw_fd());
    pump_stdin_async(ptmx_r);
    pump_stdout_blocking(ptmx_w);

    let mut status: c_int = -1;

    unsafe {
        loop {
            if waitpid(pid, &mut status, 0) == -1 && *__errno() != EINTR {
                continue;
            }
            break;
        }
    }

    exit(status)
}

pub fn prepare_pty() -> Result<()> {
    let tty_in = isatty(stdin());
    let tty_out = isatty(stdout());
    let tty_err = isatty(stderr());
    if !tty_in && !tty_out && !tty_err {
        return Ok(());
    }

    let mut pts_path = format!("{}/{}", get_tmp_path(), PTS_NAME);
    if !std::path::Path::new(&pts_path).exists() {
        pts_path = "/dev/pts".to_string();
    }
    let ptmx_path = format!("{}/ptmx", pts_path);
    let ptmx_fd = open(ptmx_path, OFlags::RDWR, Mode::empty())?;
    grantpt(&ptmx_fd)?;
    unlockpt(&ptmx_fd)?;
    let pty_num = get_pty_num(&ptmx_fd)?;
    create_transfer(ptmx_fd)?;
    setsid()?;
    let pty_fd = open(format!("{pts_path}/{pty_num}"), OFlags::RDWR, Mode::empty())?;
    if tty_in {
        dup2_stdin(&pty_fd)?;
    }
    if tty_out {
        dup2_stdout(&pty_fd)?;
    }
    if tty_err {
        dup2_stderr(&pty_fd)?;
    }
    Ok(())
}
