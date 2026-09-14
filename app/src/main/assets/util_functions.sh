#!/system/bin/sh
#######################################################################################
# Helper Functions (credits to topjohnwu)
#######################################################################################
APATCH_VER='0.10.4'
APATCH_VER_CODE=164

ui_print() {
  if $BOOTMODE; then
    echo "$1"
  else
    echo -e "ui_print $1\nui_print" >> /proc/self/fd/$OUTFD
  fi
}

toupper() {
  echo "$@" | tr '[:lower:]' '[:upper:]'
}

grep_cmdline() {
  local REGEX="s/^$1=//p"
  { echo $(cat /proc/cmdline)$(sed -e 's/[^"]//g' -e 's/""//g' /proc/cmdline) | xargs -n 1; \
    sed -e 's/ = /=/g' -e 's/, /,/g' -e 's/"//g' /proc/bootconfig; \
  } 2>/dev/null | sed -n "$REGEX"
}

grep_prop() {
  local REGEX="s/^$1=//p"
  shift
  local FILES=$@
  [ -z "$FILES" ] && FILES='/system/build.prop'
  cat $FILES 2>/dev/null | dos2unix | sed -n "$REGEX" | head -n 1
}

getvar() {
  local VARNAME=$1
  local VALUE
  local PROPPATH='/data/.magisk /cache/.magisk'
  [ ! -z $MAGISKTMP ] && PROPPATH="$MAGISKTMP/.magisk/config $PROPPATH"
  VALUE=$(grep_prop $VARNAME $PROPPATH)
  [ ! -z $VALUE ] && eval $VARNAME=\$VALUE
}

is_mounted() {
  grep -q " $(readlink -f $1) " /proc/mounts 2>/dev/null
  return $?
}
abort() {
  ui_print "$1"
  $BOOTMODE || recovery_cleanup
  [ ! -z $MODPATH ] && rm -rf $MODPATH
  rm -rf $TMPDIR
  exit 1
}
set_nvbase() {
  NVBASE="$1"
  MAGISKBIN="$1/magisk"
}

print_title() {
  local len line1len line2len bar
  line1len=$(echo -n $1 | wc -c)
  line2len=$(echo -n $2 | wc -c)
  len=$line2len
  [ $line1len -gt $line2len ] && len=$line1len
  len=$((len + 2))
  bar=$(printf "%${len}s" | tr ' ' '*')
  ui_print "$bar"
  ui_print " $1 "
  [ "$2" ] && ui_print " $2 "
  ui_print "$bar"
}
setup_flashable() {
  ensure_bb
  $BOOTMODE && return
  if [ -z $OUTFD ] || readlink /proc/$$/fd/$OUTFD | grep -q /tmp; then
    # We will have to manually find out OUTFD
    for FD in $(ls /proc/$$/fd); do
      if readlink /proc/$$/fd/$FD | grep -q pipe; then
        if ps | grep -v grep | grep -qE " 3 $FD |status_fd=$FD"; then
          OUTFD=$FD
          break
        fi
      fi
    done
  fi
  recovery_actions
}

ensure_bb() {
  if set -o | grep -q standalone; then
    # We are definitely in busybox ash
    set -o standalone
    return
  fi

  # Find our busybox binary
  local bb
  if [ -f $TMPDIR/busybox ]; then
    bb=$TMPDIR/busybox
  elif [ -f $MAGISKBIN/busybox ]; then
    bb=$MAGISKBIN/busybox
  else
    abort "! Cannot find BusyBox"
  fi
  chmod 755 $bb

  # Busybox could be a script, make sure /system/bin/sh exists
  if [ ! -f /system/bin/sh ]; then
    umount -l /system 2>/dev/null
    mkdir -p /system/bin
    ln -s $(command -v sh) /system/bin/sh
  fi

  export ASH_STANDALONE=1

  # Find our current arguments
  # Run in busybox environment to ensure consistent results
  # /proc/<pid>/cmdline shall be <interpreter> <script> <arguments...>
  local cmds="$($bb sh -c "
  for arg in \$(tr '\0' '\n' < /proc/$$/cmdline); do
    if [ -z \"\$cmds\" ]; then
      # Skip the first argument as we want to change the interpreter
      cmds=\"sh\"
    else
      cmds=\"\$cmds '\$arg'\"
    fi
  done
  echo \$cmds")"

  # Re-exec our script
  echo $cmds | $bb xargs $bb
  exit
}
recovery_actions() {
  # Make sure random won't get blocked
  mount -o bind /dev/urandom /dev/random
  # Unset library paths
  OLD_LD_LIB=$LD_LIBRARY_PATH
  OLD_LD_PRE=$LD_PRELOAD
  OLD_LD_CFG=$LD_CONFIG_FILE
  unset LD_LIBRARY_PATH
  unset LD_PRELOAD
  unset LD_CONFIG_FILE
}
recovery_cleanup() {
  local DIR
  ui_print "- Unmounting partitions"
  (
  if [ ! -d /postinstall/tmp ]; then
    umount -l /system
    umount -l /system_root
  fi
  umount -l /vendor
  umount -l /persist
  umount -l /metadata
  for DIR in /apex /system /system_root; do
    if [ -L "${DIR}_link" ]; then
      rmdir $DIR
      mv -f ${DIR}_link $DIR
    fi
  done
  umount -l /dev/random
  ) 2>/dev/null
  [ -z $OLD_LD_LIB ] || export LD_LIBRARY_PATH=$OLD_LD_LIB
  [ -z $OLD_LD_PRE ] || export LD_PRELOAD=$OLD_LD_PRE
  [ -z $OLD_LD_CFG ] || export LD_CONFIG_FILE=$OLD_LD_CFG
}

find_block() {
  local BLOCK DEV DEVICE DEVNAME PARTNAME UEVENT
  for BLOCK in "$@"; do
    DEVICE=$(find /dev/block \( -type b -o -type c -o -type l \) -iname $BLOCK | head -n 1) 2>/dev/null
    if [ ! -z $DEVICE ]; then
      readlink -f $DEVICE
      return 0
    fi
  done
  # Fallback by parsing sysfs uevents
  for UEVENT in /sys/dev/block/*/uevent; do
    DEVNAME=$(grep_prop DEVNAME $UEVENT)
    PARTNAME=$(grep_prop PARTNAME $UEVENT)
    for BLOCK in "$@"; do
      if [ "$(toupper $BLOCK)" = "$(toupper $PARTNAME)" ]; then
        echo /dev/block/$DEVNAME
        return 0
      fi
    done
  done
  # Look just in /dev in case we're dealing with MTD/NAND without /dev/block devices/links
  for DEV in "$@"; do
    DEVICE=$(find /dev \( -type b -o -type c -o -type l \) -maxdepth 1 -iname $DEV | head -n 1) 2>/dev/null
    if [ ! -z $DEVICE ]; then
      readlink -f $DEVICE
      return 0
    fi
  done
  return 1
}

# After calling this method, the following variables will be set:
# SLOT
get_current_slot() {
  # Check A/B slot
  SLOT=$(grep_cmdline androidboot.slot_suffix)
  if [ -z $SLOT ]; then
    SLOT=$(grep_cmdline androidboot.slot)
    [ -z $SLOT ] || SLOT=_${SLOT}
  fi
  if [ -z $SLOT ]; then
    SLOT=$(getprop ro.boot.slot_suffix)
  fi
  [ "$SLOT" = "normal" ] && unset SLOT
  [ -z $SLOT ] || echo "SLOT=$SLOT"
}

# After calling this method, the following variables will be set:
# SLOT
# This is used after OTA
get_next_slot() {
  # Check A/B slot
  SLOT=$(grep_cmdline androidboot.slot_suffix)
  if [ -z $SLOT ]; then
    SLOT=$(grep_cmdline androidboot.slot)
    [ -z $SLOT ] || SLOT=_${SLOT}
  fi
  if [ -z $SLOT ]; then
    SLOT=$(getprop ro.boot.slot_suffix)
  fi
   [ -z $SLOT ] && { >&2 echo "can't determined next boot slot! check your devices is A/B"; exit 1; }
   [ "$SLOT" = "normal" ] &&  { >&2 echo "can't determined next boot slot! check your devices is A/B"; exit 1; }
  if [[ $SLOT == *_a ]]; then
    SLOT='_b'
  else
    SLOT='_a'
  fi
  echo "SLOT=$SLOT"
}

find_boot_image() {
  if [ ! -z $SLOT ]; then
    BOOTIMAGE=$(find_block "boot$SLOT")
  fi
  if [ -z $BOOTIMAGE ]; then
    BOOTIMAGE=$(find_block kern-a android_boot kernel bootimg boot lnx boot_a)
  fi
  if [ -z $BOOTIMAGE ]; then
    # Lets see what fstabs tells me
    BOOTIMAGE=$(grep -v '#' /etc/*fstab* | grep -E '/boot(img)?[^a-zA-Z]' | grep -oE '/dev/[a-zA-Z0-9_./-]*' | head -n 1)
  fi
  [ -z $BOOTIMAGE ] || echo "BOOTIMAGE=$BOOTIMAGE"
}

# Read boot header integers (Android's supported ARM64 host is little-endian)
# and AVB footer integers (big-endian), without depending on an AVB algorithm.
boot_read_le32() {
  od -An -v -j "$2" -N 4 -tu4 "$1" | tr -d ' \n'
}

boot_read_be64() {
  local hex
  hex=$(od -An -v -j "$2" -N 8 -tx1 "$1" | tr -d ' \n') || return 1
  # Shell arithmetic is signed; reject sizes outside its positive range.
  [ "${#hex}" -eq 16 ] || return 1
  case "$hex" in [89abcdefABCDEF]*) return 1 ;; esac
  echo "$((0x$hex))"
}

boot_write_be64() {
  local shift byte
  for shift in 56 48 40 32 24 16 8 0; do
    byte=$((($1 >> shift) & 255))
    printf "\\$(printf '%03o' "$byte")"
  done
}

# kptools 0.13.8 searches for an AVB0 header with a hard-coded libavb version.
# On Pixel 11 (libavb 1.4 / ML-DSA) it selects an embedded GKI RSA certificate
# instead. Locate the real metadata through the original AVBf footer, and move
# that footer's offsets by the kernel's change in padded size. The metadata,
# signature algorithm, rollback index and build properties stay byte-identical.
repair_boot_avb_footer() (
  set -o pipefail
  local original="$1" repacked="$2"
  local original_size repacked_size footer magic
  if [ -b "$original" ]; then
    original_size=$(blockdev --getsize64 "$original") || return 1
  else
    original_size=$(stat -c '%s' "$original") || return 1
  fi
  [ "$original_size" -ge 64 ] || return 0
  footer=$((original_size - 64))
  magic=$(od -An -v -j "$footer" -N 4 -tx1 "$original" | tr -d ' \n') || return 1
  [ "$magic" = 41564266 ] || return 0

  repacked_size=$(stat -c '%s' "$repacked") || return 1
  [ "$repacked_size" -ge 64 ] || return 1
  local version page_size old_kernel new_kernel delta
  version=$(boot_read_le32 "$original" 40) || return 1
  [ "$version" = "$(boot_read_le32 "$repacked" 40)" ] || return 1
  case "$version" in
    0|1|2) page_size=$(boot_read_le32 "$original" 36) || return 1
           [ "$page_size" = "$(boot_read_le32 "$repacked" 36)" ] || return 1 ;;
    3|4) page_size=4096 ;;
    *) return 1 ;;
  esac
  [ "$page_size" -ge 512 ] && [ $((page_size & (page_size - 1))) -eq 0 ] || return 1
  old_kernel=$(boot_read_le32 "$original" 8) || return 1
  new_kernel=$(boot_read_le32 "$repacked" 8) || return 1
  [ "$old_kernel" -gt 0 ] && [ "$new_kernel" -gt 0 ] || return 1
  delta=$(( ((new_kernel + page_size - 1) / page_size - (old_kernel + page_size - 1) / page_size) * page_size ))

  local old_payload old_offset metadata_size new_payload new_offset old_hash new_hash
  old_payload=$(boot_read_be64 "$original" "$((footer + 12))") || return 1
  old_offset=$(boot_read_be64 "$original" "$((footer + 20))") || return 1
  metadata_size=$(boot_read_be64 "$original" "$((footer + 28))") || return 1
  # Subtraction avoids overflow when validating untrusted footer lengths.
  [ "$old_payload" -ge "$((page_size + (old_kernel + page_size - 1) / page_size * page_size))" ] || return 1
  [ "$old_offset" -ge "$old_payload" ] && [ "$old_offset" -le "$footer" ] || return 1
  [ "$metadata_size" -ge 256 ] && [ "$metadata_size" -le "$((footer - old_offset))" ] || return 1
  new_payload=$((old_payload + delta))
  new_offset=$((old_offset + delta))
  [ "$new_payload" -ge 0 ] && [ "$new_offset" -ge "$new_payload" ] || return 1
  [ "$new_offset" -le "$((repacked_size - 64))" ] || return 1
  [ "$metadata_size" -le "$((repacked_size - 64 - new_offset))" ] || return 1
  magic=$(od -An -v -j "$old_offset" -N 4 -tx1 "$original" | tr -d ' \n') || return 1
  [ "$magic" = 41564230 ] || return 1

  # Fail before flashing if the repacker discarded, truncated or changed the
  # original metadata. Never try to recreate it by scanning for magic bytes.
  old_hash=$(dd if="$original" bs=1 skip="$old_offset" count="$metadata_size" 2>/dev/null | sha256sum) || return 1
  new_hash=$(dd if="$repacked" bs=1 skip="$new_offset" count="$metadata_size" 2>/dev/null | sha256sum) || return 1
  [ "$old_hash" = "$new_hash" ] || return 1

  dd if="$original" of="$repacked" bs=1 skip="$footer" seek="$((repacked_size - 64))" count=64 conv=notrunc 2>/dev/null || return 1
  boot_write_be64 "$new_payload" | dd of="$repacked" bs=1 seek="$((repacked_size - 52))" conv=notrunc 2>/dev/null || return 1
  boot_write_be64 "$new_offset" | dd of="$repacked" bs=1 seek="$((repacked_size - 44))" conv=notrunc 2>/dev/null || return 1
  echo "- Preserved original AVB metadata at offset $new_offset"
)

flash_image() {
  local CMD1
  case "$1" in
    *.gz) CMD1="gzip -d < '$1' 2>/dev/null";;
    *)    CMD1="cat '$1'";;
  esac
  if [ -b "$2" ]; then {
      local img_sz=$(stat -c '%s' "$1")
      local blk_sz=$(blockdev --getsize64 "$2")
      local blk_bs=$(blockdev --getbsz "$2")
      [ "$img_sz" -gt "$blk_sz" ] && return 1
      blockdev --setrw "$2"
      local blk_ro=$(blockdev --getro "$2")
      [ "$blk_ro" -eq 1 ] && return 2
      eval "$CMD1" | dd of="$2" bs="$blk_bs" iflag=fullblock conv=notrunc,fsync 2>/dev/null
      sync
  } elif [ -c "$2" ]; then {
      flash_eraseall "$2" >&2
      eval "$CMD1" | nandwrite -p "$2" - >&2
  } else {
      echo "- Not block or char device, storing image"
      eval "$CMD1" > "$2" 2>/dev/null
  } fi
  return 0
}

setup_mntpoint() {
  local POINT=$1
  [ -L $POINT ] && mv -f $POINT ${POINT}_link
  if [ ! -d $POINT ]; then
    rm -f $POINT
    mkdir -p $POINT
  fi
}

mount_name() {
  local PART=$1
  local POINT=$2
  local FLAG=$3
  setup_mntpoint $POINT
  is_mounted $POINT && return
  # First try mounting with fstab
  mount $FLAG $POINT 2>/dev/null
  if ! is_mounted $POINT; then
    local BLOCK=$(find_block $PART)
    mount $FLAG $BLOCK $POINT || return
  fi
  ui_print "- Mounting $POINT"
}

mount_ro_ensure() {
  # We handle ro partitions only in recovery
  $BOOTMODE && return
  local PART=$1
  local POINT=$2
  mount_name "$PART" $POINT '-o ro'
  is_mounted $POINT || abort "! Cannot mount $POINT"
}

# After calling this method, the following variables will be set:
# SLOT, SYSTEM_AS_ROOT, LEGACYSAR
mount_partitions() {
  # Check A/B slot
  SLOT=$(grep_cmdline androidboot.slot_suffix)
  if [ -z $SLOT ]; then
    SLOT=$(grep_cmdline androidboot.slot)
    [ -z $SLOT ] || SLOT=_${SLOT}
  fi
  [ "$SLOT" = "normal" ] && unset SLOT
  [ -z $SLOT ] || ui_print "- Current boot slot: $SLOT"

  # Mount ro partitions
  if is_mounted /system_root; then
    umount /system 2>/dev/null
    umount /system_root 2>/dev/null
  fi
  mount_ro_ensure "system$SLOT app$SLOT" /system
  if [ -f /system/init -o -L /system/init ]; then
    SYSTEM_AS_ROOT=true
    setup_mntpoint /system_root
    if ! mount --move /system /system_root; then
      umount /system
      umount -l /system 2>/dev/null
      mount_ro_ensure "system$SLOT app$SLOT" /system_root
    fi
    mount -o bind /system_root/system /system
  else
    if grep ' / ' /proc/mounts | grep -qv 'rootfs' || grep -q ' /system_root ' /proc/mounts; then
      SYSTEM_AS_ROOT=true
    else
      SYSTEM_AS_ROOT=false
    fi
  fi
  $SYSTEM_AS_ROOT && ui_print "- Device is system-as-root"

  LEGACYSAR=false
  if $BOOTMODE; then
    grep ' / ' /proc/mounts | grep -q '/dev/root' && LEGACYSAR=true
  else
    # Recovery mode, assume devices that don't use dynamic partitions are legacy SAR
    local IS_DYNAMIC=false
    if grep -q 'androidboot.super_partition' /proc/cmdline; then
      IS_DYNAMIC=true
    elif [ -n "$(find_block super)" ]; then
      IS_DYNAMIC=true
    fi
    if $SYSTEM_AS_ROOT && ! $IS_DYNAMIC; then
      LEGACYSAR=true
      ui_print "- Legacy SAR, force kernel to load rootfs"
    fi
  fi
}

get_flags() {
  if grep ' /data ' /proc/mounts | grep -q 'dm-'; then
    ISENCRYPTED=true
  elif [ "$(getprop ro.crypto.state)" = "encrypted" ]; then
    ISENCRYPTED=true
  elif [ "$DATA" = "false" ]; then
    # No data access means unable to decrypt in recovery
    ISENCRYPTED=true
  else
    ISENCRYPTED=false
  fi
  if [ -n "$(find_block vbmeta vbmeta_a)" ]; then
    PATCHVBMETAFLAG=false
  else
    PATCHVBMETAFLAG=true
    ui_print "- No vbmeta partition, patch vbmeta in boot image"
  fi

  # Overridable config flags with safe defaults
  getvar KEEPVERITY
  getvar KEEPFORCEENCRYPT
  getvar RECOVERYMODE
  if [ -z $KEEPVERITY ]; then
    if $SYSTEM_AS_ROOT; then
      KEEPVERITY=true
      ui_print "- System-as-root, keep dm-verity"
    else
      KEEPVERITY=false
    fi
  fi
  if [ -z $KEEPFORCEENCRYPT ]; then
    if $ISENCRYPTED; then
      KEEPFORCEENCRYPT=true
      ui_print "- Encrypted data, keep forceencrypt"
    else
      KEEPFORCEENCRYPT=false
    fi
  fi
  [ -z $RECOVERYMODE ] && RECOVERYMODE=false
}

install_apatch() {
  cd $MAGISKBIN

  # Source the boot patcher
  SOURCEDMODE=true
  . ./boot_patch.sh "$BOOTIMAGE"
  ui_print "- Flashing new boot image"
  flash_image new-boot.img "$BOOTIMAGE"
  case $? in
    1)
      abort "! Insufficient partition size"
      ;;
    2)
      abort "! $BOOTIMAGE is read only"
      ;;
  esac
  
  rm -f new-boot.img

  run_migrations
}

check_data() {
  DATA=false
  DATA_DE=false
  if grep ' /data ' /proc/mounts | grep -vq 'tmpfs'; then
    # Test if data is writable
    touch /data/.rw && rm /data/.rw && DATA=true
    # Test if data is decrypted
    $DATA && [ -d /data/adb ] && touch /data/adb/.rw && rm /data/adb/.rw && DATA_DE=true
    $DATA_DE && [ -d /data/adb/magisk ] || mkdir /data/adb/magisk || DATA_DE=false
  fi
  set_nvbase "/data"
  $DATA || set_nvbase "/cache/data_adb"
  $DATA_DE && set_nvbase "/data/adb"
}

api_level_arch_detect() {
  API=$(grep_get_prop ro.build.version.sdk)
  ABI=$(grep_get_prop ro.product.cpu.abi)
  if [ "$ABI" = "x86" ]; then
    ARCH=x86
    ABI32=x86
    IS64BIT=false
  elif [ "$ABI" = "arm64-v8a" ]; then
    ARCH=arm64
    ABI32=armeabi-v7a
    IS64BIT=true
  elif [ "$ABI" = "x86_64" ]; then
    ARCH=x64
    ABI32=x86
    IS64BIT=true
  else
    ARCH=arm
    ABI=armeabi-v7a
    ABI32=armeabi-v7a
    IS64BIT=false
  fi
}

remove_system_su() {
  [ -d /postinstall/tmp ] && POSTINST=/postinstall
  cd $POSTINST/system
  if [ -f bin/su -o -f xbin/su ] && [ ! -f /su/bin/su ]; then
    ui_print "- Removing system installed root"
    blockdev --setrw /dev/block/mapper/system$SLOT 2>/dev/null
    mount -o rw,remount $POSTINST/system
    # SuperSU
    cd bin
    if [ -e .ext/.su ]; then
      mv -f app_process32_original app_process32 2>/dev/null
      mv -f app_process64_original app_process64 2>/dev/null
      mv -f install-recovery_original.sh install-recovery.sh 2>/dev/null
      if [ -e app_process64 ]; then
        ln -sf app_process64 app_process
      elif [ -e app_process32 ]; then
        ln -sf app_process32 app_process
      fi
    fi
    # More SuperSU, SuperUser & ROM su
    cd ..
    rm -rf .pin bin/.ext etc/.installed_su_daemon etc/.has_su_daemon \
    xbin/daemonsu xbin/su xbin/sugote xbin/sugote-mksh xbin/supolicy \
    bin/app_process_init bin/su /cache/su lib/libsupol.so lib64/libsupol.so \
    su.d etc/init.d/99SuperSUDaemon etc/install-recovery.sh /cache/install-recovery.sh \
    .supersu /cache/.supersu /data/.supersu \
    app/Superuser.apk app/SuperSU /cache/Superuser.apk
  elif [ -f /cache/su.img -o -f /data/su.img -o -d /data/su -o -d /data/adb/su ]; then
    ui_print "- Removing systemless installed root"
    umount -l /su 2>/dev/null
    rm -rf /cache/su.img /data/su.img /data/su /data/adb/su /data/adb/suhide \
    /cache/.supersu /data/.supersu /cache/supersu_install /data/supersu_install
  fi
  cd $TMPDIR
}

run_migrations() {
  local LOCSHA1
  local TARGET
  # Legacy app installation
  local BACKUP=$MAGISKBIN/stock_boot*.gz
  if [ -f $BACKUP ]; then
    cp $BACKUP /data
    rm -f $BACKUP
  fi

  # Legacy backup
  for gz in /data/stock_boot*.gz; do
    [ -f $gz ] || break
    LOCSHA1=$(basename $gz | sed -e 's/stock_boot_//' -e 's/.img.gz//')
    [ -z $LOCSHA1 ] && break
    mkdir /data/magisk_backup_${LOCSHA1} 2>/dev/null
    mv $gz /data/magisk_backup_${LOCSHA1}/boot.img.gz
  done

  # Stock backups
  LOCSHA1=$SHA1
  for name in boot dtb dtbo dtbs; do
    BACKUP=$MAGISKBIN/stock_${name}.img
    [ -f $BACKUP ] || continue
    if [ $name = 'boot' ]; then
      LOCSHA1=$($MAGISKBIN/kptools sha1 $BACKUP)
      mkdir /data/magisk_backup_${LOCSHA1} 2>/dev/null
    fi
    TARGET=/data/magisk_backup_${LOCSHA1}/${name}.img
    cp $BACKUP $TARGET
    rm -f $BACKUP
    gzip -9f $TARGET
  done
}