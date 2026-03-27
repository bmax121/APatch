use anyhow::{bail, Context, Result};
use clap::Parser;
use policy::{format_statement_help, SePolicy};
use std::io::{self, Write};
use std::path::PathBuf;

/// Write adapter for formatting
struct WriteAdapter<T>(T);

impl<T: Write> std::fmt::Write for WriteAdapter<T> {
    fn write_str(&mut self, s: &str) -> std::fmt::Result {
        self.0.write_all(s.as_bytes()).map_err(|_| std::fmt::Error)
    }
}

/// MagiskPolicy - SELinux Policy Patch Tool
#[derive(Debug, clap::Args)]
#[allow(clippy::struct_excessive_bools)]
pub struct Args {
    /// Load monolithic sepolicy from FILE
    #[arg(long = "load", value_name = "FILE")]
    load: Option<PathBuf>,

    /// Load from precompiled sepolicy or compile split cil policies
    #[arg(long = "load-split")]
    load_split: bool,

    /// Compile split cil policies
    #[arg(long = "compile-split")]
    compile_split: bool,

    /// Dump monolithic sepolicy to FILE
    #[arg(long = "save", value_name = "FILE")]
    save: Option<PathBuf>,

    /// Immediately load sepolicy into the kernel
    #[arg(long = "live")]
    live: bool,

    /// Apply built-in Magisk sepolicy rules
    #[arg(long = "magisk")]
    magisk: bool,

    /// Apply rules from FILE, read and parsed line by line as policy statements
    #[arg(long = "apply", value_name = "FILE")]
    apply: Vec<PathBuf>,

    /// Print all rules in the loaded sepolicy
    #[arg(long = "print-rules")]
    print_rules: bool,

    /// Policy statements to apply
    #[arg(required = false)]
    policies: Vec<String>,
}

#[derive(Parser)]
#[command(
    name = "magiskpolicy",
    version,
    about = "SELinux Policy Patch Tool",
    disable_help_subcommand = true
)]
struct MagiskPolicyParser {
    #[command(flatten)]
    arg: Args,
}

pub fn policy_main(args: &[String]) -> ! {
    if let Err(err) = run_from_args(args) {
        eprintln!("magiskpolicy: {err:#}");
        std::process::exit(1);
    }
    std::process::exit(0);
}

/// Entry point for magiskpolicy multicall.
///
/// `args` should include argv[0] (the program name).
fn run_from_args(args: &[String]) -> Result<()> {
    let parser = match MagiskPolicyParser::try_parse_from(args) {
        Ok(cli) => cli,
        Err(err) => {
            if err.kind() == clap::error::ErrorKind::DisplayHelp {
                print_usage(args.first().map(|s| s.as_str()).unwrap_or("magiskpolicy"));
                return Ok(());
            }
            if err.kind() == clap::error::ErrorKind::DisplayVersion {
                err.print()?;
                return Ok(());
            }
            return Err(anyhow::anyhow!("{err}"));
        }
    };
    execute(&parser.arg)
}

pub fn get_policy_main(args: &[String]) -> Result<SePolicy> {
    let parser = MagiskPolicyParser::try_parse_from(args)?;
    let cli = parser.arg;

    // Validate mutually exclusive options
    let load_count = cli.load.iter().count()
        + cli.compile_split as usize
        + cli.load_split as usize;
    if load_count > 1 {
        bail!("Multiple load source supplied");
    }

    // Load policy
    let mut sepol = if let Some(ref file) = cli.load {
        SePolicy::from_file(file)
            .with_context(|| format!("Cannot load policy from {}", file.display()))?
    } else if cli.load_split {
        SePolicy::from_split().context("Cannot load split policy")?
    } else if cli.compile_split {
        SePolicy::compile_split().context("Cannot compile split policy")?
    } else {
        SePolicy::from_file("/sys/fs/selinux/policy")
            .context("Cannot load live policy")?
    };
    execute_next(&cli, &mut sepol)?;
    Ok(sepol)
}

/// Execute magiskpolicy logic
/// Subcommand will direct call that, skip run_from_args
pub fn execute(cli: &Args) -> Result<()> {
    // Validate mutually exclusive options
    let load_count = cli.load.iter().count()
        + cli.compile_split as usize
        + cli.load_split as usize;
    if load_count > 1 {
        bail!("Multiple load source supplied");
    }

    // Load policy
    let mut sepol = if let Some(ref file) = cli.load {
        SePolicy::from_file(file)
            .with_context(|| format!("Cannot load policy from {}", file.display()))?
    } else if cli.load_split {
        SePolicy::from_split().context("Cannot load split policy")?
    } else if cli.compile_split {
        SePolicy::compile_split().context("Cannot compile split policy")?
    } else {
        SePolicy::from_file("/sys/fs/selinux/policy")
            .context("Cannot load live policy")?
    };

    execute_next(cli, &mut sepol)?;
    Ok(())
}
fn execute_next(cli: &Args, sepol: &mut SePolicy) -> Result<()> {
    if cli.print_rules {
        if cli.magisk
            || !cli.apply.is_empty()
            || !cli.policies.is_empty()
            || cli.live
            || cli.save.is_some()
        {
            bail!("Cannot print rules with other options");
        }
        sepol.print_rules();
        return Ok(());
    }

    if cli.magisk {
        sepol.magisk_rules();
    }

    for file in &cli.apply {
        sepol
            .load_rule_file(file)
            .with_context(|| format!("Cannot load rule file {}", file.display()))?;
    }

    for statement in &cli.policies {
        sepol.load_rules(statement);
    }

    if cli.live {
        sepol
            .to_file("/sys/fs/selinux/load")
            .context("Cannot apply policy")?;
    }

    if let Some(ref file) = cli.save {
        sepol
            .to_file(file)
            .with_context(|| format!("Cannot dump policy to {}", file.display()))?;
    }
    Ok(())
}

/// Print usage information
fn print_usage(cmd: &str) {
    eprintln!(
        r#"MagiskPolicy - SELinux Policy Patch Tool

Usage: {cmd} [--options...] [policy statements...]

Options:
   --help            show help message for policy statements
   --load FILE       load monolithic sepolicy from FILE
   --load-split      load from precompiled sepolicy or compile
                     split cil policies
   --compile-split   compile split cil policies
   --save FILE       dump monolithic sepolicy to FILE
   --live            immediately load sepolicy into the kernel
   --magisk          apply built-in Magisk sepolicy rules
   --apply FILE      apply rules from FILE, read and parsed
                     line by line as policy statements
                     (multiple --apply are allowed)
   --print-rules     print all rules in the loaded sepolicy

If neither --load, --load-split, nor --compile-split is specified,
it will load from current live policies (/sys/fs/selinux/policy)
"#
    );

    let _ = format_statement_help(&mut WriteAdapter(io::stderr()));
    eprintln!();
}
