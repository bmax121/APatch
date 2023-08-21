setup_py = find_program('../setup.py')
setup_py = [setup_py.path(), '--quiet', '--top-builddir', meson.project_build_root()]

custom_target(
  'pylibfdt',
  input: 'libfdt.i',
  output: '_libfdt.so',
  command: [setup_py, 'build_ext', '--build-lib=' + meson.current_build_dir()],
  build_by_default: true,
)

meson.add_install_script(setup_py, 'install', '--prefix=' + get_option('prefix'), '--root=$DESTDIR')
