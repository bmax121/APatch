version_script = '-Wl,--version-script=@0@'.format(meson.current_source_dir() / 'version.lds')
if not cc.has_link_argument(version_script)
  version_script = []
endif

sources = files(
  'fdt.c',
  'fdt_addresses.c',
  'fdt_check.c',
  'fdt_empty_tree.c',
  'fdt_overlay.c',
  'fdt_ro.c',
  'fdt_rw.c',
  'fdt_strerror.c',
  'fdt_sw.c',
  'fdt_wip.c',
)

libfdt = library(
  'fdt', sources,
  version: '1.6.0',
  link_args: ['-Wl,--no-undefined', version_script],
  link_depends: 'version.lds',
  install: true,
)

libfdt_a = static_library(
  'fdt', sources,
  install: true,
)

libfdt_inc = include_directories('.')

libfdt_dep = declare_dependency(
  include_directories: libfdt_inc,
  link_with: libfdt,
)

install_headers(
  files(
    'fdt.h',
    'libfdt.h',
    'libfdt_env.h',
  )
)

pkgconfig = import('pkgconfig')

pkgconfig.generate(
  libraries: libfdt,
  version: meson.project_version(),
  filebase: 'libfdt',
  name: 'libfdt',
  description: 'Flat Device Tree manipulation',
)
