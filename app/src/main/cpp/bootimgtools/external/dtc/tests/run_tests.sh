#! /bin/sh

SRCDIR=`dirname "$0"`
. "$SRCDIR/testutils.sh"

if [ -z "$CC" ]; then
    CC=cc
fi

if [ -z "$PYTHON" ]; then
    PYTHON=python3
fi

if [ -n "$NO_PYTHON" ]; then
    if [ "$NO_PYTHON" != "0" ]; then
        no_python=true
    else
        no_python=false
    fi
else
    if [ -f ../pylibfdt/_libfdt.so ] || [ -f ../pylibfdt/_libfdt.cpython-3*.so ]; then
        no_python=false
    else
        no_python=true
    fi
fi

if [ -n "$NO_YAML" ]; then
    if [ "$NO_YAML" != "0" ]; then
        no_yaml=true
    else
        no_yaml=false
    fi
else
    if pkg-config --exists yaml-0.1; then
        no_yaml=false
    else
        no_yaml=true
    fi
fi

# stat differs between platforms
if [ -z "$STATSZ" ]; then
	stat --version 2>/dev/null | grep -q 'GNU'
	GNUSTAT=$?
	if [ "$GNUSTAT" -ne 0 ]; then
		# Assume BSD stat if we can't detect as GNU stat
		STATSZ="stat -f %Uz"
	else
		STATSZ="stat -c %s"
	fi
fi

# Help things find the libfdt shared object
if [ -z "$TEST_LIBDIR" ]; then
    TEST_LIBDIR=../libfdt
fi
export LD_LIBRARY_PATH="$TEST_LIBDIR"

export QUIET_TEST=1
STOP_ON_FAIL=0

export VALGRIND=
VGCODE=126

tot_tests=0
tot_pass=0
tot_fail=0
tot_config=0
tot_vg=0
tot_strange=0

base_run_test() {
    tot_tests=$((tot_tests + 1))
    if VALGRIND="$VALGRIND" "$@"; then
	tot_pass=$((tot_pass + 1))
    else
	ret="$?"
	if [ "$STOP_ON_FAIL" -eq 1 ]; then
	    exit 1
	fi
	if [ "$ret" -eq 1 ]; then
	    tot_config=$((tot_config + 1))
	elif [ "$ret" -eq 2 ]; then
	    tot_fail=$((tot_fail + 1))
	elif [ "$ret" -eq $VGCODE ]; then
	    tot_vg=$((tot_vg + 1))
	else
	    tot_strange=$((tot_strange + 1))
	fi
    fi
}

shorten_echo () {
    limit=32
    printf "$1"
    shift
    for x; do
	if [ ${#x} -le $limit ]; then
	    printf " $x"
	else
	    short=$(echo "$x" | head -c$limit)
	    printf " \"$short\"...<${#x} bytes>"
	fi
    done
}

run_test () {
    printf "$*:	"
    if [ -n "$VALGRIND" -a -f $1.supp ]; then
	VGSUPP="--suppressions=$1.supp"
    fi
    base_run_test $VALGRIND $VGSUPP "./$@"
}

run_sh_test () {
    printf "$*:	"
    base_run_test sh "$@"
}

wrap_test () {
    (
	if verbose_run "$@"; then
	    PASS
	else
	    ret="$?"
	    if [ "$ret" -gt 127 ]; then
		signame=$(kill -l $((ret - 128)))
		FAIL "Killed by SIG$signame"
	    elif [ "$ret" -eq $VGCODE ]; then
		echo "VALGRIND ERROR"
		exit $VGCODE
	    else
		FAIL "Returned error code $ret"
	    fi
	fi
    )
}

run_wrap_test () {
    shorten_echo "$@:	"
    base_run_test wrap_test "$@"
}

wrap_error () {
    (
	if verbose_run "$@"; then
	    FAIL "Expected non-zero return code"
	else
	    ret="$?"
	    if [ "$ret" -gt 127 ]; then
		signame=$(kill -l $((ret - 128)))
		FAIL "Killed by SIG$signame"
	    else
		PASS
	    fi
	fi
    )
}

run_wrap_error_test () {
    shorten_echo "$@"
    printf " {!= 0}:	"
    base_run_test wrap_error "$@"
}

# $1: dtb file
# $2: align base
check_align () {
    shorten_echo "check_align $@:	"
    local size=$($STATSZ "$1")
    local align="$2"
    (
	if [ $(($size % $align)) -eq 0 ] ;then
	    PASS
	else
	    FAIL "Output size $size is not $align-byte aligned"
	fi
    )
}

run_dtc_test () {
    printf "dtc $*:	"
    base_run_test wrap_test $VALGRIND $DTC "$@"
}

asm_to_so () {
    $CC -shared -o $1.test.so "$SRCDIR/data.S" $1.test.s
}

asm_to_so_test () {
    run_wrap_test asm_to_so "$@"
}

run_fdtget_test () {
    expect="$1"
    shift
    printf "fdtget-runtest.sh %s $*:	" "$(echo $expect)"
    base_run_test sh "$SRCDIR/fdtget-runtest.sh" "$expect" "$@"
}

run_fdtput_test () {
    expect="$1"
    shift
    shorten_echo fdtput-runtest.sh "$expect" "$@"
    printf ":	"
    base_run_test sh "$SRCDIR/fdtput-runtest.sh" "$expect" "$@"
}

run_fdtdump_test() {
    file="$1"
    shorten_echo fdtdump-runtest.sh "$file"
    printf ":	"
    base_run_test sh "$SRCDIR/fdtdump-runtest.sh" "$file" 2>/dev/null
}

run_fdtoverlay_test() {
    expect="$1"
    shift
    shorten_echo fdtoverlay-runtest.sh "$expect" "$@"
    printf ":	"
    base_run_test sh "$SRCDIR/fdtoverlay-runtest.sh" "$expect" "$@"
}

BAD_FIXUP_TREES="bad_index \
		empty \
		empty_index \
		index_trailing \
		path_empty_prop \
		path_only \
		path_only_sep \
		path_prop"

# Test to exercise libfdt overlay application without dtc's overlay support
libfdt_overlay_tests () {
    # First test a doctored overlay which requires only local fixups
    run_dtc_test -I dts -O dtb -o overlay_base_no_symbols.test.dtb "$SRCDIR/overlay_base.dts"
    run_test check_path overlay_base_no_symbols.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_base_no_symbols.test.dtb not-exists "/__fixups__"
    run_test check_path overlay_base_no_symbols.test.dtb not-exists "/__local_fixups__"

    run_dtc_test -I dts -O dtb -o overlay_overlay_no_fixups.test.dtb "$SRCDIR/overlay_overlay_no_fixups.dts"
    run_test check_path overlay_overlay_no_fixups.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_overlay_no_fixups.test.dtb not-exists "/__fixups__"
    run_test check_path overlay_overlay_no_fixups.test.dtb exists "/__local_fixups__"

    run_test overlay overlay_base_no_symbols.test.dtb overlay_overlay_no_fixups.test.dtb

    # Then test with manually constructed fixups
    run_dtc_test -I dts -O dtb -o overlay_base_manual_symbols.test.dtb "$SRCDIR/overlay_base_manual_symbols.dts"
    run_test check_path overlay_base_manual_symbols.test.dtb exists "/__symbols__"
    run_test check_path overlay_base_manual_symbols.test.dtb not-exists "/__fixups__"
    run_test check_path overlay_base_manual_symbols.test.dtb not-exists "/__local_fixups__"

    run_dtc_test -I dts -O dtb -o overlay_overlay_manual_fixups.test.dtb "$SRCDIR/overlay_overlay_manual_fixups.dts"
    run_test check_path overlay_overlay_manual_fixups.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_overlay_manual_fixups.test.dtb exists "/__fixups__"
    run_test check_path overlay_overlay_manual_fixups.test.dtb exists "/__local_fixups__"

    run_test overlay overlay_base_manual_symbols.test.dtb overlay_overlay_manual_fixups.test.dtb

    # test simplified plugin syntax
    run_dtc_test -@ -I dts -O dtb -o overlay_overlay_simple.dtb "$SRCDIR/overlay_overlay_simple.dts"

    # verify non-generation of local fixups
    run_test check_path overlay_overlay_simple.dtb not-exists "/__local_fixups__"

    # Bad fixup tests
    for test in $BAD_FIXUP_TREES; do
	tree="overlay_bad_fixup_$test"
	run_dtc_test -I dts -O dtb -o $tree.test.dtb "$SRCDIR/$tree.dts"
	run_test overlay_bad_fixup overlay_base_no_symbols.test.dtb $tree.test.dtb
    done
}

# Tests to exercise dtc's overlay generation support
dtc_overlay_tests () {
    # Overlay tests for dtc
    run_dtc_test -@ -I dts -O dtb -o overlay_base.test.dtb "$SRCDIR/overlay_base.dts"
    run_test check_path overlay_base.test.dtb exists "/__symbols__"
    run_test check_path overlay_base.test.dtb not-exists "/__fixups__"
    run_test check_path overlay_base.test.dtb not-exists "/__local_fixups__"

    # With syntactic sugar
    run_dtc_test -I dts -O dtb -o overlay_overlay.test.dtb "$SRCDIR/overlay_overlay.dts"
    run_test check_path overlay_overlay.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_overlay.test.dtb exists "/__fixups__"
    run_test check_path overlay_overlay.test.dtb exists "/__local_fixups__"

    # Without syntactic sugar
    run_dtc_test -I dts -O dtb -o overlay_overlay_nosugar.test.dtb "$SRCDIR/overlay_overlay_nosugar.dts"
    run_test check_path overlay_overlay_nosugar.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_overlay_nosugar.test.dtb exists "/__fixups__"
    run_test check_path overlay_overlay_nosugar.test.dtb exists "/__local_fixups__"

    # Using target-path
    run_dtc_test -I dts -O dtb -o overlay_overlay_bypath.test.dtb "$SRCDIR/overlay_overlay_bypath.dts"
    run_test check_path overlay_overlay_bypath.test.dtb not-exists "/__symbols__"
    run_test check_path overlay_overlay_bypath.test.dtb not-exists "/__fixups__"
    run_test check_path overlay_overlay_bypath.test.dtb exists "/__local_fixups__"

    # Make sure local target references are resolved and nodes are merged and that path references are not
    run_dtc_test -I dts -O dtb -o overlay_overlay_local_merge.test.dtb "$SRCDIR/overlay_overlay_local_merge.dts"
    run_test check_path overlay_overlay_local_merge.test.dtb exists "/fragment@0/__overlay__/new-node/new-merged-node"
    run_test check_path overlay_overlay_local_merge.test.dtb exists "/fragment@1/__overlay__/new-root-node"

    # Check building works the same as manual constructions
    run_test dtbs_equal_ordered overlay_overlay.test.dtb overlay_overlay_nosugar.test.dtb

    run_dtc_test -I dts -O dtb -o overlay_overlay_manual_fixups.test.dtb "$SRCDIR/overlay_overlay_manual_fixups.dts"
    run_test dtbs_equal_ordered overlay_overlay.test.dtb overlay_overlay_manual_fixups.test.dtb

    run_dtc_test -I dts -O dtb -o overlay_overlay_no_fixups.test.dtb "$SRCDIR/overlay_overlay_no_fixups.dts"
    run_test dtbs_equal_ordered overlay_overlay_bypath.test.dtb overlay_overlay_no_fixups.test.dtb

    # Check we can actually apply the result
    run_dtc_test -I dts -O dtb -o overlay_base_no_symbols.test.dtb "$SRCDIR/overlay_base.dts"
    run_test overlay overlay_base.test.dtb overlay_overlay.test.dtb
    run_test overlay overlay_base_no_symbols.test.dtb overlay_overlay_bypath.test.dtb

    # test plugin source to dtb and back
    run_dtc_test -I dtb -O dts -o overlay_overlay_decompile.test.dts overlay_overlay.test.dtb
    run_dtc_test -I dts -O dtb -o overlay_overlay_decompile.test.dtb overlay_overlay_decompile.test.dts
    run_test dtbs_equal_ordered overlay_overlay.test.dtb overlay_overlay_decompile.test.dtb

    # Test generation of aliases instead of symbols
    run_dtc_test -A -I dts -O dtb -o overlay_base_with_aliases.dtb "$SRCDIR/overlay_base.dts"
    run_test check_path overlay_base_with_aliases.dtb exists "/aliases"
    run_test check_path overlay_base_with_aliases.dtb not-exists "/__symbols__"
    run_test check_path overlay_base_with_aliases.dtb not-exists "/__fixups__"
    run_test check_path overlay_base_with_aliases.dtb not-exists "/__local_fixups__"
}

tree1_tests () {
    TREE=$1

    # Read-only tests
    run_test get_mem_rsv $TREE
    run_test root_node $TREE
    run_test find_property $TREE
    run_test subnode_offset $TREE
    run_test path_offset $TREE
    run_test get_name $TREE
    run_test getprop $TREE
    run_test get_prop_offset $TREE
    run_test get_phandle $TREE
    run_test get_path $TREE
    run_test supernode_atdepth_offset $TREE
    run_test parent_offset $TREE
    run_test node_offset_by_prop_value $TREE
    run_test node_offset_by_phandle $TREE
    run_test node_check_compatible $TREE
    run_test node_offset_by_compatible $TREE
    run_test notfound $TREE

    # Write-in-place tests
    run_test setprop_inplace $TREE
    run_test nop_property $TREE
    run_test nop_node $TREE
}

tree1_tests_rw () {
    TREE=$1

    # Read-write tests
    run_test set_name $TREE
    run_test setprop $TREE
    run_test del_property $TREE
    run_test del_node $TREE
}

check_tests () {
    tree="$1"
    shift
    run_sh_test "$SRCDIR/dtc-checkfails.sh" "$@" -- -I dts -O dtb $tree
    run_dtc_test -I dts -O dtb -o $tree.test.dtb -f $tree
    run_sh_test "$SRCDIR/dtc-checkfails.sh" "$@" -- -I dtb -O dtb $tree.test.dtb
}

ALL_LAYOUTS="mts mst tms tsm smt stm"

libfdt_tests () {
    tree1_tests test_tree1.dtb

    run_dtc_test -I dts -O dtb -o addresses.test.dtb "$SRCDIR/addresses.dts"
    run_test addr_size_cells addresses.test.dtb
    run_dtc_test -I dts -O dtb -o addresses2.test.dtb "$SRCDIR/empty.dts"
    run_test addr_size_cells2 addresses2.test.dtb

    run_dtc_test -I dts -O dtb -o stringlist.test.dtb "$SRCDIR/stringlist.dts"
    run_test stringlist stringlist.test.dtb

    for flags in default no_name_dedup; do
        # Sequential write tests
        run_test sw_tree1 fixed $flags
        tree1_tests sw_tree1.test.dtb
        tree1_tests unfinished_tree1.test.dtb
        run_test dtbs_equal_ordered test_tree1.dtb sw_tree1.test.dtb
        run_test sw_states

        # Resizing tests
        for mode in resize realloc newalloc; do
            run_test sw_tree1 $mode $flags
            tree1_tests sw_tree1.test.dtb
            tree1_tests unfinished_tree1.test.dtb
            run_test dtbs_equal_ordered test_tree1.dtb sw_tree1.test.dtb
        done
    done

    # fdt_move tests
    for tree in test_tree1.dtb sw_tree1.test.dtb unfinished_tree1.test.dtb; do
	rm -f moved.$tree shunted.$tree deshunted.$tree
	run_test move_and_save $tree
	run_test dtbs_equal_ordered $tree moved.$tree
	run_test dtbs_equal_ordered $tree shunted.$tree
	run_test dtbs_equal_ordered $tree deshunted.$tree
    done

    # v16 and alternate layout tests
    for tree in test_tree1.dtb; do
	for version in 17 16; do
	    for layout in $ALL_LAYOUTS; do
		run_test mangle-layout $tree $version $layout
		tree1_tests v$version.$layout.$tree
		run_test dtbs_equal_ordered $tree v$version.$layout.$tree
	    done
	done
    done

    # Read-write tests
    for basetree in test_tree1.dtb; do
	for version in 17 16; do
	    for layout in $ALL_LAYOUTS; do
		tree=v$version.$layout.$basetree
		rm -f opened.$tree repacked.$tree
		run_test open_pack $tree
		tree1_tests opened.$tree
		tree1_tests repacked.$tree

		tree1_tests_rw $tree
		tree1_tests_rw opened.$tree
		tree1_tests_rw repacked.$tree
	    done
	done
    done
    run_test rw_tree1
    tree1_tests rw_tree1.test.dtb
    tree1_tests_rw rw_tree1.test.dtb
    run_test appendprop1
    run_test appendprop2 appendprop1.test.dtb
    run_dtc_test -I dts -O dtb -o appendprop.test.dtb "$SRCDIR/appendprop.dts"
    run_test dtbs_equal_ordered appendprop2.test.dtb appendprop.test.dtb
    libfdt_overlay_tests

    for basetree in test_tree1.dtb sw_tree1.test.dtb rw_tree1.test.dtb; do
	run_test nopulate $basetree
	run_test dtbs_equal_ordered $basetree noppy.$basetree
	tree1_tests noppy.$basetree
	tree1_tests_rw noppy.$basetree
    done

    run_test rw_oom

    run_dtc_test -I dts -O dtb -o subnode_iterate.dtb "$SRCDIR/subnode_iterate.dts"
    run_test subnode_iterate subnode_iterate.dtb

    run_dtc_test -I dts -O dtb -o property_iterate.dtb "$SRCDIR/property_iterate.dts"
    run_test property_iterate property_iterate.dtb

    run_dtc_test -I dts -O dtb -o unit-addr-without-reg.dtb "$SRCDIR/unit-addr-without-reg.dts"
    run_test appendprop_addrrange unit-addr-without-reg.dtb 1 1 1
    run_test appendprop_addrrange unit-addr-without-reg.dtb 2 2 2
    run_test appendprop_addrrange unit-addr-without-reg.dtb 2 1 3

    # Tests for behaviour on various sorts of corrupted trees
    run_test truncated_property
    run_test truncated_string
    run_test truncated_memrsv

    # Check aliases support in fdt_path_offset
    run_dtc_test -I dts -O dtb -o aliases.dtb "$SRCDIR/aliases.dts"
    run_test get_alias aliases.dtb
    run_test path_offset_aliases aliases.dtb

    # Specific bug tests
    run_test add_subnode_with_nops
    run_dtc_test -I dts -O dts -o sourceoutput.test.dts "$SRCDIR/sourceoutput.dts"
    run_dtc_test -I dts -O dtb -o sourceoutput.test.dtb "$SRCDIR/sourceoutput.dts"
    run_dtc_test -I dts -O dtb -o sourceoutput.test.dts.test.dtb sourceoutput.test.dts
    run_test dtbs_equal_ordered sourceoutput.test.dtb sourceoutput.test.dts.test.dtb

    run_dtc_test -I dts -O dtb -o embedded_nul.test.dtb "$SRCDIR/embedded_nul.dts"
    run_dtc_test -I dts -O dtb -o embedded_nul_equiv.test.dtb "$SRCDIR/embedded_nul_equiv.dts"
    run_test dtbs_equal_ordered embedded_nul.test.dtb embedded_nul_equiv.test.dtb

    run_dtc_test -I dts -O dtb "$SRCDIR/bad-size-cells.dts"

    run_wrap_error_test $DTC division-by-zero.dts
    run_wrap_error_test $DTC bad-octal-literal.dts
    run_dtc_test -I dts -O dtb "$SRCDIR/nul-in-escape.dts"
    run_wrap_error_test $DTC nul-in-line-info1.dts
    run_wrap_error_test $DTC nul-in-line-info2.dts

    run_wrap_error_test $DTC -I dtb -O dts -o /dev/null ovf_size_strings.dtb

    run_test check_header test_tree1.dtb

    FSBASE=fs
    rm -rf $FSBASE
    mkdir -p $FSBASE
    run_test fs_tree1 $FSBASE/test_tree1
    run_dtc_test -I fs -O dts -o fs.test_tree1.test.dts $FSBASE/test_tree1
    run_dtc_test -I fs -O dtb -o fs.test_tree1.test.dtb $FSBASE/test_tree1
    run_test dtbs_equal_unordered -m fs.test_tree1.test.dtb test_tree1.dtb

    # check full tests
    for good in test_tree1.dtb; do
	run_test check_full $good
    done
    for bad in truncated_property.dtb truncated_string.dtb \
		truncated_memrsv.dtb two_roots.dtb named_root.dtb; do
	run_test check_full -n $bad
    done
}

dtc_tests () {
    run_dtc_test -I dts -O dtb -o dtc_tree1.test.dtb "$SRCDIR/test_tree1.dts"
    tree1_tests dtc_tree1.test.dtb
    tree1_tests_rw dtc_tree1.test.dtb
    run_test dtbs_equal_ordered dtc_tree1.test.dtb test_tree1.dtb

    run_dtc_test -I dts -O dtb -o dtc_escapes.test.dtb "$SRCDIR/propname_escapes.dts"
    run_test propname_escapes dtc_escapes.test.dtb

    run_dtc_test -I dts -O dtb -o line_directives.test.dtb "$SRCDIR/line_directives.dts"

    run_dtc_test -I dts -O dtb -o dtc_escapes.test.dtb "$SRCDIR/escapes.dts"
    run_test string_escapes dtc_escapes.test.dtb

    run_dtc_test -I dts -O dtb -o dtc_char_literal.test.dtb "$SRCDIR/char_literal.dts"
    run_test char_literal dtc_char_literal.test.dtb

    run_dtc_test -I dts -O dtb -o dtc_sized_cells.test.dtb "$SRCDIR/sized_cells.dts"
    run_test sized_cells dtc_sized_cells.test.dtb

    run_dtc_test -I dts -O dtb -o dtc_extra-terminating-null.test.dtb "$SRCDIR/extra-terminating-null.dts"
    run_test extra-terminating-null dtc_extra-terminating-null.test.dtb

    run_dtc_test -I dts -O dtb -o dtc_references.test.dtb "$SRCDIR/references.dts"
    run_test references dtc_references.test.dtb

    run_dtc_test -I dts -O dtb -o dtc_path-references.test.dtb "$SRCDIR/path-references.dts"
    run_test path-references dtc_path-references.test.dtb

    run_test phandle_format dtc_references.test.dtb epapr
    for f in legacy epapr both; do
	    run_dtc_test -I dts -O dtb -H $f -o dtc_references.test.$f.dtb "$SRCDIR/references.dts"
	run_test phandle_format dtc_references.test.$f.dtb $f
    done

    run_dtc_test -I dts -O dtb -o multilabel.test.dtb "$SRCDIR/multilabel.dts"
    run_test references multilabel.test.dtb

    run_dtc_test -I dts -O dtb -o label_repeated.test.dtb "$SRCDIR/label_repeated.dts"

    run_dtc_test -I dts -O dtb -o dtc_comments.test.dtb "$SRCDIR/comments.dts"
    run_dtc_test -I dts -O dtb -o dtc_comments-cmp.test.dtb "$SRCDIR/comments-cmp.dts"
    run_test dtbs_equal_ordered dtc_comments.test.dtb dtc_comments-cmp.test.dtb

    # Check /include/ directive
    run_dtc_test -I dts -O dtb -o includes.test.dtb "$SRCDIR/include0.dts"
    run_test dtbs_equal_ordered includes.test.dtb test_tree1.dtb

    # Check /incbin/ directive
    run_dtc_test -I dts -O dtb -o incbin.test.dtb "$SRCDIR/incbin.dts"
    run_test incbin "$SRCDIR/incbin.bin" incbin.test.dtb

    # Check boot_cpuid_phys handling
    run_dtc_test -I dts -O dtb -o boot_cpuid.test.dtb "$SRCDIR/boot-cpuid.dts"
    run_test boot-cpuid boot_cpuid.test.dtb 16

    run_dtc_test -I dts -O dtb -b 17 -o boot_cpuid_17.test.dtb "$SRCDIR/boot-cpuid.dts"
    run_test boot-cpuid boot_cpuid_17.test.dtb 17

    run_dtc_test -I dtb -O dtb -o preserve_boot_cpuid.test.dtb boot_cpuid.test.dtb
    run_test boot-cpuid preserve_boot_cpuid.test.dtb 16
    run_test dtbs_equal_ordered preserve_boot_cpuid.test.dtb boot_cpuid.test.dtb

    run_dtc_test -I dtb -O dtb -o preserve_boot_cpuid_17.test.dtb boot_cpuid_17.test.dtb
    run_test boot-cpuid preserve_boot_cpuid_17.test.dtb 17
    run_test dtbs_equal_ordered preserve_boot_cpuid_17.test.dtb boot_cpuid_17.test.dtb

    run_dtc_test -I dtb -O dtb -b17 -o override17_boot_cpuid.test.dtb boot_cpuid.test.dtb
    run_test boot-cpuid override17_boot_cpuid.test.dtb 17

    run_dtc_test -I dtb -O dtb -b0 -o override0_boot_cpuid_17.test.dtb boot_cpuid_17.test.dtb
    run_test boot-cpuid override0_boot_cpuid_17.test.dtb 0


    # Check -Oasm mode
    for tree in test_tree1.dts escapes.dts references.dts path-references.dts \
	comments.dts aliases.dts include0.dts incbin.dts \
	value-labels.dts ; do
	run_dtc_test -I dts -O asm -o oasm_$tree.test.s "$SRCDIR/$tree"
	asm_to_so_test oasm_$tree
	run_dtc_test -I dts -O dtb -o $tree.test.dtb "$SRCDIR/$tree"
	run_test asm_tree_dump ./oasm_$tree.test.so oasm_$tree.test.dtb
	run_wrap_test cmp oasm_$tree.test.dtb $tree.test.dtb
    done

    run_test value-labels ./oasm_value-labels.dts.test.so

    # Check -Odts mode preserve all dtb information
    for tree in test_tree1.dtb dtc_tree1.test.dtb dtc_escapes.test.dtb \
	dtc_extra-terminating-null.test.dtb dtc_references.test.dtb; do
	run_dtc_test -I dtb -O dts -o odts_$tree.test.dts $tree
	run_dtc_test -I dts -O dtb -o odts_$tree.test.dtb odts_$tree.test.dts
	run_test dtbs_equal_ordered $tree odts_$tree.test.dtb
    done

    # Check -Odts preserving type information
    for tree in type-preservation.dts; do
        run_dtc_test -I dts -O dts -o $tree.test.dts "$SRCDIR/$tree"
        run_dtc_test -I dts -O dts $tree.test.dts
        run_wrap_test cmp "$SRCDIR/$tree" $tree.test.dts
    done
    for tree in path-references; do
        run_dtc_test -I dts -O dtb -o $tree.test.dtb "$SRCDIR/$tree.dts"
        run_dtc_test -I dts -O dts -o $tree.test.dts "$SRCDIR/$tree.dts"
        run_dtc_test -I dts -O dtb -o $tree.test.dts.test.dtb $tree.test.dts
        run_test dtbs_equal_ordered $tree.test.dtb $tree.test.dts.test.dtb
    done

    # Check -Oyaml output
    if ! $no_yaml; then
            for tree in type-preservation; do
                run_dtc_test -I dts -O yaml -o $tree.test.dt.yaml "$SRCDIR/$tree.dts"
                run_wrap_test cmp "$SRCDIR/$tree.dt.yaml" $tree.test.dt.yaml
            done
    fi

    # Check version conversions
    for tree in test_tree1.dtb ; do
	 for aver in 1 2 3 16 17; do
	     atree="ov${aver}_$tree.test.dtb"
	     run_dtc_test -I dtb -O dtb -V$aver -o $atree $tree
	     for bver in 16 17; do
		 btree="ov${bver}_$atree"
		 run_dtc_test -I dtb -O dtb -V$bver -o $btree $atree
		 run_test dtbs_equal_ordered $btree $tree
	     done
	 done
    done

    # Check merge/overlay functionality
    run_dtc_test -I dts -O dtb -o dtc_tree1_merge.test.dtb "$SRCDIR/test_tree1_merge.dts"
    tree1_tests dtc_tree1_merge.test.dtb test_tree1.dtb
    run_dtc_test -I dts -O dtb -o dtc_tree1_merge_labelled.test.dtb "$SRCDIR/test_tree1_merge_labelled.dts"
    tree1_tests dtc_tree1_merge_labelled.test.dtb test_tree1.dtb
    run_dtc_test -I dts -O dtb -o dtc_tree1_label_noderef.test.dtb "$SRCDIR/test_tree1_label_noderef.dts"
    run_test dtbs_equal_unordered dtc_tree1_label_noderef.test.dtb test_tree1.dtb
    run_dtc_test -I dts -O dtb -o multilabel_merge.test.dtb "$SRCDIR/multilabel_merge.dts"
    run_test references multilabel.test.dtb
    run_test dtbs_equal_ordered multilabel.test.dtb multilabel_merge.test.dtb
    run_dtc_test -I dts -O dtb -o dtc_tree1_merge_path.test.dtb "$SRCDIR/test_tree1_merge_path.dts"
    tree1_tests dtc_tree1_merge_path.test.dtb test_tree1.dtb
    run_wrap_error_test $DTC -I dts -O dtb -o /dev/null "$SRCDIR/test_label_ref.dts"

    # Check prop/node delete functionality
    run_dtc_test -I dts -O dtb -o dtc_tree1_delete.test.dtb "$SRCDIR/test_tree1_delete.dts"
    tree1_tests dtc_tree1_delete.test.dtb

    # Check omit-if-no-ref functionality
    run_dtc_test -I dts -O dtb -o omit-no-ref.test.dtb "$SRCDIR/omit-no-ref.dts"
    run_test check_path omit-no-ref.test.dtb not-exists "/node1"
    run_test check_path omit-no-ref.test.dtb not-exists "/node2"
    run_test check_path omit-no-ref.test.dtb exists "/node3"
    run_test check_path omit-no-ref.test.dtb exists "/node4"

    run_dtc_test -I dts -O dts -o delete_reinstate_multilabel.dts.test.dts "$SRCDIR/delete_reinstate_multilabel.dts"
    run_wrap_test cmp delete_reinstate_multilabel.dts.test.dts "$SRCDIR/delete_reinstate_multilabel_ref.dts"

    # Check some checks
    check_tests "$SRCDIR/dup-nodename.dts" duplicate_node_names
    check_tests "$SRCDIR/dup-propname.dts" duplicate_property_names
    check_tests "$SRCDIR/dup-phandle.dts" explicit_phandles
    check_tests "$SRCDIR/zero-phandle.dts" explicit_phandles
    check_tests "$SRCDIR/minusone-phandle.dts" explicit_phandles
    run_sh_test "$SRCDIR/dtc-checkfails.sh" phandle_references -- -I dts -O dtb "$SRCDIR/nonexist-node-ref.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" phandle_references -- -I dts -O dtb "$SRCDIR/nonexist-label-ref.dts"
    run_sh_test "$SRCDIR/dtc-fatal.sh" -I dts -O dtb "$SRCDIR/nonexist-node-ref2.dts"
    check_tests "$SRCDIR/bad-name-property.dts" name_properties

    check_tests "$SRCDIR/bad-ncells.dts" address_cells_is_cell size_cells_is_cell interrupts_extended_is_cell
    check_tests "$SRCDIR/bad-string-props.dts" device_type_is_string model_is_string status_is_string label_is_string compatible_is_string_list names_is_string_list
    check_tests "$SRCDIR/bad-chosen.dts" chosen_node_is_root
    check_tests "$SRCDIR/bad-chosen.dts" chosen_node_bootargs
    check_tests "$SRCDIR/bad-chosen.dts" chosen_node_stdout_path
    check_tests "$SRCDIR/bad-reg-ranges.dts" reg_format ranges_format
    check_tests "$SRCDIR/bad-empty-ranges.dts" ranges_format
    check_tests "$SRCDIR/reg-ranges-root.dts" reg_format ranges_format
    check_tests "$SRCDIR/bad-dma-ranges.dts" dma_ranges_format
    check_tests "$SRCDIR/default-addr-size.dts" avoid_default_addr_size
    check_tests "$SRCDIR/obsolete-chosen-interrupt-controller.dts" obsolete_chosen_interrupt_controller
    check_tests "$SRCDIR/reg-without-unit-addr.dts" unit_address_vs_reg
    check_tests "$SRCDIR/unit-addr-without-reg.dts" unit_address_vs_reg
    check_tests "$SRCDIR/unit-addr-leading-0x.dts" unit_address_format
    check_tests "$SRCDIR/unit-addr-leading-0s.dts" unit_address_format
    check_tests "$SRCDIR/unit-addr-unique.dts" unique_unit_address
    check_tests "$SRCDIR/bad-phandle-cells.dts" interrupts_extended_property
    check_tests "$SRCDIR/bad-gpio.dts" gpios_property
    check_tests "$SRCDIR/good-gpio.dts" -n gpios_property
    check_tests "$SRCDIR/bad-graph.dts" graph_child_address
    check_tests "$SRCDIR/bad-graph.dts" graph_port
    check_tests "$SRCDIR/bad-graph.dts" graph_endpoint
    run_sh_test "$SRCDIR/dtc-checkfails.sh" deprecated_gpio_property -- -Wdeprecated_gpio_property -I dts -O dtb "$SRCDIR/bad-gpio.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" -n deprecated_gpio_property -- -Wdeprecated_gpio_property -I dts -O dtb "$SRCDIR/good-gpio.dts"
    check_tests "$SRCDIR/bad-interrupt-cells.dts" interrupts_property
    check_tests "$SRCDIR/bad-interrupt-controller.dts" interrupt_provider
    check_tests "$SRCDIR/bad-interrupt-map.dts" interrupt_map
    check_tests "$SRCDIR/bad-interrupt-map-parent.dts" interrupt_map
    check_tests "$SRCDIR/bad-interrupt-map-mask.dts" interrupt_map
    run_sh_test "$SRCDIR/dtc-checkfails.sh" node_name_chars -- -I dtb -O dtb bad_node_char.dtb
    run_sh_test "$SRCDIR/dtc-checkfails.sh" node_name_format -- -I dtb -O dtb bad_node_format.dtb
    run_sh_test "$SRCDIR/dtc-checkfails.sh" property_name_chars -- -I dtb -O dtb bad_prop_char.dtb

    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label1.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label2.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label3.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label4.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label5.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" duplicate_label -- -I dts -O dtb "$SRCDIR/reuse-label6.dts"

    run_test check_path test_tree1.dtb exists "/subnode@1"
    run_test check_path test_tree1.dtb not-exists "/subnode@10"

    check_tests "$SRCDIR/pci-bridge-ok.dts" -n pci_bridge
    check_tests "$SRCDIR/pci-bridge-bad1.dts" pci_bridge
    check_tests "$SRCDIR/pci-bridge-bad2.dts" pci_bridge

    check_tests "$SRCDIR/unit-addr-simple-bus-reg-mismatch.dts" simple_bus_reg
    check_tests "$SRCDIR/unit-addr-simple-bus-compatible.dts" simple_bus_reg


    # Check warning options
    run_sh_test "$SRCDIR/dtc-checkfails.sh" address_cells_is_cell interrupts_extended_is_cell -n size_cells_is_cell -- -Wno_size_cells_is_cell -I dts -O dtb "$SRCDIR/bad-ncells.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" -n test-warn-output.test.dtb -I dts -O dtb "$SRCDIR/bad-ncells.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" test-error-output.test.dtb -I dts -O dtb bad-ncells.dts -Esize_cells_is_cell
    run_sh_test "$SRCDIR/dtc-checkfails.sh" always_fail -- -Walways_fail -I dts -O dtb "$SRCDIR/test_tree1.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" -n always_fail -- -Walways_fail -Wno_always_fail -I dts -O dtb "$SRCDIR/test_tree1.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" test-negation-1.test.dtb -Ealways_fail -I dts -O dtb "$SRCDIR/test_tree1.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" -n test-negation-2.test.dtb -Ealways_fail -Eno_always_fail -I dts -O dtb "$SRCDIR/test_tree1.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" test-negation-3.test.dtb -Ealways_fail -Wno_always_fail -I dts -O dtb "$SRCDIR/test_tree1.dts"
    run_sh_test "$SRCDIR/dtc-fails.sh" -n test-negation-4.test.dtb -Esize_cells_is_cell -Eno_size_cells_is_cell -I dts -O dtb "$SRCDIR/bad-ncells.dts"
    run_sh_test "$SRCDIR/dtc-checkfails.sh" size_cells_is_cell -- -Esize_cells_is_cell -Eno_size_cells_is_cell -I dts -O dtb "$SRCDIR/bad-ncells.dts"

    # Check for proper behaviour reading from stdin
    run_dtc_test -I dts -O dtb -o stdin_dtc_tree1.test.dtb - < "$SRCDIR/test_tree1.dts"
    run_wrap_test cmp stdin_dtc_tree1.test.dtb dtc_tree1.test.dtb
    run_dtc_test -I dtb -O dts -o stdin_odts_test_tree1.dtb.test.dts - < test_tree1.dtb
    run_wrap_test cmp stdin_odts_test_tree1.dtb.test.dts odts_test_tree1.dtb.test.dts

    # Check integer expresisons
    run_test integer-expressions -g integer-expressions.test.dts
    run_dtc_test -I dts -O dtb -o integer-expressions.test.dtb integer-expressions.test.dts
    run_test integer-expressions integer-expressions.test.dtb

    # Check for graceful failure in some error conditions
    run_sh_test "$SRCDIR/dtc-fatal.sh" -I dts -O dtb nosuchfile.dts
    run_sh_test "$SRCDIR/dtc-fatal.sh" -I dtb -O dtb nosuchfile.dtb
    run_sh_test "$SRCDIR/dtc-fatal.sh" -I fs -O dtb nosuchfile

    # Dependencies
    run_dtc_test -I dts -O dtb -o dependencies.test.dtb -d dependencies.test.d "$SRCDIR/dependencies.dts"
    sed -i.bak "s,$SRCDIR/,,g" dependencies.test.d
    run_wrap_test cmp dependencies.test.d "$SRCDIR/dependencies.cmp"

    # Search paths
    run_wrap_error_test $DTC -I dts -O dtb -o search_paths.dtb "$SRCDIR/search_paths.dts"
    run_dtc_test -i "$SRCDIR/search_dir" -I dts -O dtb -o search_paths.dtb \
	"$SRCDIR/search_paths.dts"
    run_wrap_error_test $DTC -i "$SRCDIR/search_dir_b" -I dts -O dtb \
	-o search_paths_b.dtb "$SRCDIR/search_paths_b.dts"
    run_dtc_test -i "$SRCDIR/search_dir_b" -i "$SRCDIR/search_dir" -I dts -O dtb \
	-o search_paths_b.dtb "$SRCDIR/search_paths_b.dts"
    run_dtc_test -I dts -O dtb -o search_paths_subdir.dtb \
	"$SRCDIR/search_dir_b/search_paths_subdir.dts"

    # Check -a option
    for align in 2 4 8 16 32 64; do
	# -p -a
	run_dtc_test -O dtb -p 1000 -a $align -o align0.dtb "$SRCDIR/subnode_iterate.dts"
	base_run_test check_align align0.dtb $align
	# -S -a
	run_dtc_test -O dtb -S 1999 -a $align -o align1.dtb "$SRCDIR/subnode_iterate.dts"
	base_run_test check_align align1.dtb $align
    done

    # Tests for overlay/plugin generation
    dtc_overlay_tests
}

cmp_tests () {
    basetree="$1"
    shift
    wrongtrees="$@"

    run_test dtb_reverse $basetree

    # First dtbs_equal_ordered
    run_test dtbs_equal_ordered $basetree $basetree
    run_test dtbs_equal_ordered -n $basetree $basetree.reversed.test.dtb
    for tree in $wrongtrees; do
	run_test dtbs_equal_ordered -n $basetree $tree
    done

    # now unordered
    run_test dtbs_equal_unordered $basetree $basetree
    run_test dtbs_equal_unordered $basetree $basetree.reversed.test.dtb
    run_test dtbs_equal_unordered $basetree.reversed.test.dtb $basetree
    for tree in $wrongtrees; do
	run_test dtbs_equal_unordered -n $basetree $tree
    done

    # now dtc --sort
    run_dtc_test -I dtb -O dtb -s -o $basetree.sorted.test.dtb $basetree
    run_test dtbs_equal_unordered $basetree $basetree.sorted.test.dtb
    run_dtc_test -I dtb -O dtb -s -o $basetree.reversed.sorted.test.dtb $basetree.reversed.test.dtb
    run_test dtbs_equal_unordered $basetree.reversed.test.dtb $basetree.reversed.sorted.test.dtb
    run_test dtbs_equal_ordered $basetree.sorted.test.dtb $basetree.reversed.sorted.test.dtb
}

dtbs_equal_tests () {
    WRONG_TREE1=""
    for x in 1 2 3 4 5 6 7 8 9; do
	run_dtc_test -I dts -O dtb -o test_tree1_wrong$x.test.dtb "$SRCDIR/test_tree1_wrong$x.dts"
	WRONG_TREE1="$WRONG_TREE1 test_tree1_wrong$x.test.dtb"
    done
    cmp_tests test_tree1.dtb $WRONG_TREE1
}

fdtget_tests () {
    dts=label01.dts
    dtb=$dts.fdtget.test.dtb
    run_dtc_test -O dtb -o $dtb "$SRCDIR/$dts"

    # run_fdtget_test <expected-result> [<flags>] <file> <node> <property>
    run_fdtget_test "MyBoardName" $dtb / model
    run_fdtget_test "MyBoardName MyBoardFamilyName" $dtb / compatible
    run_fdtget_test "77 121 66 111 \
97 114 100 78 97 109 101 0 77 121 66 111 97 114 100 70 97 109 105 \
108 121 78 97 109 101 0" -t bu $dtb / compatible
    run_fdtget_test "MyBoardName MyBoardFamilyName" -t s $dtb / compatible
    run_fdtget_test 32768 $dtb /cpus/PowerPC,970@1 d-cache-size
    run_fdtget_test 8000 -tx $dtb /cpus/PowerPC,970@1 d-cache-size
    run_fdtget_test "61 62 63 0" -tbx $dtb /randomnode tricky1
    run_fdtget_test "a b c d de ea ad be ef" -tbx $dtb /randomnode blob
    run_fdtget_test "MyBoardName\0MyBoardFamilyName\0" -tr $dtb / compatible
    run_fdtget_test "\x0a\x0b\x0c\x0d\xde\xea\xad\xbe\xef" -tr $dtb /randomnode blob

    # Here the property size is not a multiple of 4 bytes, so it should fail
    run_wrap_error_test $DTGET -tlx $dtb /randomnode mixed
    run_fdtget_test "6162 6300 1234 0 a 0 b 0 c" -thx $dtb /randomnode mixed
    run_fdtget_test "61 62 63 0 12 34 0 0 0 a 0 0 0 b 0 0 0 c" \
	-thhx $dtb /randomnode mixed
    run_wrap_error_test $DTGET -ts $dtb /randomnode doctor-who

    # Test multiple arguments
    run_fdtget_test "MyBoardName\nmemory" -ts $dtb / model /memory device_type

    # Test defaults
    run_wrap_error_test $DTGET -tx $dtb /randomnode doctor-who
    run_fdtget_test "<the dead silence>" -tx \
	-d "<the dead silence>" $dtb /randomnode doctor-who
    run_fdtget_test "<blink>" -tx -d "<blink>" $dtb /memory doctor-who
}

fdtput_tests () {
    dts=label01.dts
    dtb=$dts.fdtput.test.dtb
    text="$SRCDIR/lorem.txt"

    # Allow just enough space for $text
    run_dtc_test -O dtb -p $($STATSZ $text) -o $dtb "$SRCDIR/$dts"

    # run_fdtput_test <expected-result> <file> <node> <property> <flags> <value>
    run_fdtput_test "a_model" $dtb / model -ts "a_model"
    run_fdtput_test "board1 board2" $dtb / compatible -ts board1 board2
    run_fdtput_test "board1 board2" $dtb / compatible -ts "board1 board2"
    run_fdtput_test "32768" $dtb /cpus/PowerPC,970@1 d-cache-size "" "32768"
    run_fdtput_test "8001" $dtb /cpus/PowerPC,970@1 d-cache-size -tx 0x8001
    run_fdtput_test "2 3 12" $dtb /randomnode tricky1 -tbi "02 003 12"
    run_fdtput_test "a b c ea ad be ef" $dtb /randomnode blob \
	-tbx "a b c ea ad be ef"
    run_fdtput_test "a0b0c0d deeaae ef000000" $dtb /randomnode blob \
	-tx "a0b0c0d deeaae ef000000"
    run_fdtput_test "$(cat $text)" $dtb /randomnode blob -ts "$(cat $text)"

    # Test expansion of the blob when insufficient room for property
    run_fdtput_test "$(cat $text $text)" $dtb /randomnode blob -ts "$(cat $text $text)"

    # Start again with a fresh dtb
    run_dtc_test -O dtb -p $($STATSZ $text) -o $dtb "$SRCDIR/$dts"

    # Node creation
    run_wrap_error_test $DTPUT $dtb -c /baldrick sod
    run_wrap_test $DTPUT $dtb -c /chosen/son /chosen/daughter
    run_fdtput_test "eva" $dtb /chosen/daughter name "" -ts "eva"
    run_fdtput_test "adam" $dtb /chosen/son name "" -ts "adam"

    # Not allowed to create an existing node
    run_wrap_error_test $DTPUT $dtb -c /chosen
    run_wrap_error_test $DTPUT $dtb -c /chosen/son

    # Automatic node creation
    run_wrap_test $DTPUT $dtb -cp /blackadder/the-second/turnip \
	/blackadder/the-second/potato
    run_fdtput_test 1000 $dtb /blackadder/the-second/turnip cost "" 1000
    run_fdtput_test "fine wine" $dtb /blackadder/the-second/potato drink \
	"-ts" "fine wine"
    run_wrap_test $DTPUT $dtb -p /you/are/drunk/sir/winston slurp -ts twice

    # Test expansion of the blob when insufficient room for a new node
    run_wrap_test $DTPUT $dtb -cp "$(cat $text $text)/longish"

    # Allowed to create an existing node with -p
    run_wrap_test $DTPUT $dtb -cp /chosen
    run_wrap_test $DTPUT $dtb -cp /chosen/son

    # Start again with a fresh dtb
    run_dtc_test -O dtb -p $($STATSZ $text) -o $dtb "$SRCDIR/$dts"

    # Node delete
    run_wrap_test $DTPUT $dtb -c /chosen/node1 /chosen/node2 /chosen/node3
    run_fdtget_test "node3\nnode2\nnode1" $dtb -l  /chosen
    run_wrap_test $DTPUT $dtb -r /chosen/node1 /chosen/node2
    run_fdtget_test "node3" $dtb -l  /chosen

    # Delete the non-existent node
    run_wrap_error_test $DTPUT $dtb -r /non-existent/node

    # Property delete
    run_fdtput_test "eva" $dtb /chosen/ name "" -ts "eva"
    run_fdtput_test "016" $dtb /chosen/ age  "" -ts "016"
    run_fdtget_test "age\nname\nbootargs\nlinux,platform" $dtb -p  /chosen
    run_wrap_test $DTPUT $dtb -d /chosen/ name age
    run_fdtget_test "bootargs\nlinux,platform" $dtb -p  /chosen

    # Delete the non-existent property
    run_wrap_error_test $DTPUT $dtb -d /chosen   non-existent-prop

    # TODO: Add tests for verbose mode?
}

utilfdt_tests () {
    run_test utilfdt_test
}

fdtdump_tests () {
    run_fdtdump_test "$SRCDIR/fdtdump.dts"
}

fdtoverlay_tests() {
    base="$SRCDIR/overlay_base.dts"
    basedtb=overlay_base.fdoverlay.test.dtb
    overlay="$SRCDIR/overlay_overlay_manual_fixups.dts"
    overlaydtb=overlay_overlay_manual_fixups.fdoverlay.test.dtb
    targetdtb=target.fdoverlay.test.dtb

    run_dtc_test -@ -I dts -O dtb -o $basedtb $base
    run_dtc_test -@ -I dts -O dtb -o $overlaydtb $overlay

    # test that the new property is installed
    run_fdtoverlay_test foobar "/test-node" "test-str-property" "-ts" ${basedtb} ${targetdtb} ${overlaydtb}

    stacked_base="$SRCDIR/stacked_overlay_base.dts"
    stacked_basedtb=stacked_overlay_base.fdtoverlay.test.dtb
    stacked_bar="$SRCDIR/stacked_overlay_bar.dts"
    stacked_bardtb=stacked_overlay_bar.fdtoverlay.test.dtb
    stacked_baz="$SRCDIR/stacked_overlay_baz.dts"
    stacked_bazdtb=stacked_overlay_baz.fdtoverlay.test.dtb
    stacked_targetdtb=stacked_overlay_target.fdtoverlay.test.dtb

    run_dtc_test -@ -I dts -O dtb -o $stacked_basedtb $stacked_base
    run_dtc_test -@ -I dts -O dtb -o $stacked_bardtb $stacked_bar
    run_dtc_test -@ -I dts -O dtb -o $stacked_bazdtb $stacked_baz

    # test that baz correctly inserted the property
    run_fdtoverlay_test baz "/foonode/barnode/baznode" "baz-property" "-ts" ${stacked_basedtb} ${stacked_targetdtb} ${stacked_bardtb} ${stacked_bazdtb}

    # test that bar and baz are correctly appended to __symbols__
    run_fdtoverlay_test "/foonode/barnode" "/__symbols__"  "bar" "-ts" ${stacked_basedtb} ${stacked_targetdtb} ${stacked_bardtb}
    run_fdtoverlay_test "/foonode/barnode/baznode" "/__symbols__"  "baz" "-ts" ${stacked_basedtb} ${stacked_targetdtb} ${stacked_bardtb} ${stacked_bazdtb}

    overlay_long_path="$SRCDIR/overlay_overlay_long_path.dts"
    overlay_long_pathdtb=overlay_overlay_long_path.fdoverlay.test.dtb
    target_long_pathdtb=overlay_overlay_long_path_target.fdoverlay.test.dtb
    run_dtc_test -@ -I dts -O dtb -o $overlay_long_pathdtb $overlay_long_path

    # test that fdtoverlay manages to apply overlays with long target path
    run_fdtoverlay_test lpath "/test-node/sub-test-node/sub-test-node-with-very-long-target-path/test-0" "prop" "-ts" ${basedtb} ${target_long_pathdtb} ${overlay_long_pathdtb}

    # test adding a label to the root of a fragment
    stacked_base_nolabel="$SRCDIR/stacked_overlay_base_nolabel.dts"
    stacked_base_nolabeldtb=stacked_overlay_base_nolabel.test.dtb
    stacked_addlabel="$SRCDIR/stacked_overlay_addlabel.dts"
    stacked_addlabeldtb=stacked_overlay_addlabel.test.dtb
    stacked_addlabel_targetdtb=stacked_overlay_target_nolabel.fdtoverlay.test.dtb

    run_dtc_test -@ -I dts -O dtb -o $stacked_base_nolabeldtb $stacked_base_nolabel
    run_dtc_test -@ -I dts -O dtb -o $stacked_addlabeldtb $stacked_addlabel

    run_fdtoverlay_test baz "/foonode/barnode/baznode" "baz-property" "-ts" ${stacked_base_nolabeldtb} ${stacked_addlabel_targetdtb} ${stacked_addlabeldtb} ${stacked_bardtb} ${stacked_bazdtb}
}

pylibfdt_tests () {
    run_dtc_test -I dts -O dtb -o test_props.dtb "$SRCDIR/test_props.dts"
    TMP=/tmp/tests.stderr.$$
    $PYTHON "$SRCDIR/pylibfdt_tests.py" -v 2> $TMP

    # Use the 'ok' message meaning the test passed, 'ERROR' meaning it failed
    # and the summary line for total tests (e.g. 'Ran 17 tests in 0.002s').
    # We could add pass + fail to get total tests, but this provides a useful
    # sanity check.
    pass_count=$(grep "ok$" $TMP | wc -l)
    fail_count=$(grep "^ERROR: " $TMP | wc -l)
    total_tests=$(sed -n 's/^Ran \([0-9]*\) tests.*$/\1/p' $TMP)
    cat $TMP
    rm $TMP

    # Extract the test results and add them to our totals
    tot_fail=$((tot_fail + $fail_count))
    tot_pass=$((tot_pass + $pass_count))
    tot_tests=$((tot_tests + $total_tests))
}

while getopts "vt:me" ARG ; do
    case $ARG in
	"v")
	    unset QUIET_TEST
	    ;;
	"t")
	    TESTSETS=$OPTARG
	    ;;
	"m")
	    VALGRIND="valgrind --tool=memcheck -q --error-exitcode=$VGCODE"
	    ;;
	"e")
	    STOP_ON_FAIL=1
	    ;;
    esac
done

if [ -z "$TESTSETS" ]; then
    TESTSETS="libfdt utilfdt dtc dtbs_equal fdtget fdtput fdtdump fdtoverlay"

    # Test pylibfdt if the libfdt Python module is available.
    if ! $no_python; then
        TESTSETS="$TESTSETS pylibfdt"
    fi
fi

# Make sure we don't have stale blobs lying around
rm -f *.test.dtb *.test.dts

for set in $TESTSETS; do
    case $set in
	"libfdt")
	    libfdt_tests
	    ;;
	"utilfdt")
	    utilfdt_tests
	    ;;
	"dtc")
	    dtc_tests
	    ;;
	"dtbs_equal")
	    dtbs_equal_tests
	    ;;
	"fdtget")
	    fdtget_tests
	    ;;
	"fdtput")
	    fdtput_tests
	    ;;
	"fdtdump")
	    fdtdump_tests
	    ;;
	"pylibfdt")
	    pylibfdt_tests
	    ;;
        "fdtoverlay")
	    fdtoverlay_tests
	    ;;
    esac
done

echo "********** TEST SUMMARY"
echo "*     Total testcases:	$tot_tests"
echo "*                PASS:	$tot_pass"
echo "*                FAIL:	$tot_fail"
echo "*   Bad configuration:	$tot_config"
if [ -n "$VALGRIND" ]; then
    echo "*    valgrind errors:	$tot_vg"
fi
echo "* Strange test result:	$tot_strange"
echo "**********"

[ "$tot_tests" -eq "$tot_pass" ] || exit 1
