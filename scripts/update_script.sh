######################
# APatch Installer #
######################

OUTFD=/proc/self/fd/$2

ui_print() {
  echo -e "ui_print $1\nui_print" >> $OUTFD
}

cd $INSTALLER

/sbin/bash "addon/InstallAP.sh" "$OUTFD"

exit 0
