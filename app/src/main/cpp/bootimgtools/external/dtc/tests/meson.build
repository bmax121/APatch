trees = static_library('trees', files('trees.S'), c_args: '-D__ASSEMBLY__',
                       include_directories: libfdt_inc)

dumptrees = executable('dumptrees', files('dumptrees.c'),
                       link_with: trees, dependencies: libfdt_dep)

dumptrees_dtb = custom_target(
  'dumptrees',
  command: [dumptrees, meson.current_build_dir()],
  output: [
    'test_tree1.dtb',
    'bad_node_char.dtb',
    'bad_node_format.dtb',
    'bad_prop_char.dtb',
    'ovf_size_strings.dtb',
    'truncated_property.dtb',
    'truncated_string.dtb',
    'truncated_memrsv.dtb',
  ]
)

testutil_dep = declare_dependency(sources: ['testutils.c'], link_with: trees)

tests = [
  'add_subnode_with_nops',
  'addr_size_cells',
  'addr_size_cells2',
  'appendprop1',
  'appendprop2',
  'appendprop_addrrange',
  'boot-cpuid',
  'char_literal',
  'check_full',
  'check_header',
  'check_path',
  'del_node',
  'del_property',
  'dtb_reverse',
  'dtbs_equal_ordered',
  'dtbs_equal_unordered',
  'extra-terminating-null',
  'find_property',
  'fs_tree1',
  'get_alias',
  'get_mem_rsv',
  'get_name',
  'get_path',
  'get_phandle',
  'get_prop_offset',
  'getprop',
  'incbin',
  'integer-expressions',
  'mangle-layout',
  'move_and_save',
  'node_check_compatible',
  'node_offset_by_compatible',
  'node_offset_by_phandle',
  'node_offset_by_prop_value',
  'nop_node',
  'nop_property',
  'nopulate',
  'notfound',
  'open_pack',
  'overlay',
  'overlay_bad_fixup',
  'parent_offset',
  'path-references',
  'path_offset',
  'path_offset_aliases',
  'phandle_format',
  'property_iterate',
  'propname_escapes',
  'references',
  'root_node',
  'rw_oom',
  'rw_tree1',
  'set_name',
  'setprop',
  'setprop_inplace',
  'sized_cells',
  'string_escapes',
  'stringlist',
  'subnode_iterate',
  'subnode_offset',
  'supernode_atdepth_offset',
  'sw_states',
  'sw_tree1',
  'utilfdt_test',
]

tests += [
  'truncated_memrsv',
  'truncated_property',
  'truncated_string',
]

dl = cc.find_library('dl', required: false)
if dl.found()
  tests += [
    'asm_tree_dump',
    'value-labels',
  ]
endif

foreach t: tests
  executable(t, files(t + '.c'), dependencies: [testutil_dep, util_dep, libfdt_dep, dl])
endforeach

run_tests = find_program('run_tests.sh')

env = []
if not py.found()
  env += 'NO_PYTHON=1'
else
  env += [
    'PYTHON=' + py.path(),
    'PYTHONPATH=' + meson.source_root() / 'pylibfdt',
  ]
endif
if not yaml.found()
  env += 'NO_YAML=1'
endif

test(
  'run-test',
  run_tests,
  workdir: meson.current_build_dir(),
  depends: dumptrees_dtb,
  env: env,
  timeout: 1800, # mostly for valgrind
)
