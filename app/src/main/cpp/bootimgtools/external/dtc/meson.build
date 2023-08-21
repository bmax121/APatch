project('dtc', 'c',
  version: '1.6.0',
  license: ['GPL2+', 'BSD-2'],
  default_options: 'werror=true',
)

cc = meson.get_compiler('c')

add_project_arguments(
  cc.get_supported_arguments([
    '-Wpointer-arith',
    '-Wcast-qual',
    '-Wnested-externs',
    '-Wstrict-prototypes',
    '-Wmissing-prototypes',
    '-Wredundant-decls',
    '-Wshadow'
  ]),
  language: 'c'
)

if host_machine.system() == 'windows'
  add_project_arguments(
    '-D__USE_MINGW_ANSI_STDIO=1',
    language: 'c'
  )
endif

add_project_arguments(
  '-DFDT_ASSUME_MASK=' + get_option('assume-mask').to_string(),
  language: 'c'
)

yamltree = 'yamltree.c'
yaml = dependency('yaml-0.1', required: get_option('yaml'))
if not yaml.found()
  add_project_arguments('-DNO_YAML', language: 'c')
  yamltree = []
endif

valgrind = dependency('valgrind', required: get_option('valgrind'))
if not valgrind.found()
  add_project_arguments('-DNO_VALGRIND', language: 'c')
endif

py = import('python')
py = py.find_installation(required: get_option('python'))
swig = find_program('swig', required: get_option('python'))

version_gen_h = vcs_tag(
  input: 'version_gen.h.in',
  output: 'version_gen.h',
)

subdir('libfdt')

if get_option('tools')
  flex = find_program('flex', required: true)
  bison = find_program('bison', required: true)

  util_dep = declare_dependency(
    sources: ['util.c', version_gen_h],
    include_directories: '.',
    dependencies: libfdt_dep
  )

  lgen = generator(
    flex,
    output: '@PLAINNAME@.lex.c',
    arguments: ['-o', '@OUTPUT@', '@INPUT@'],
  )

  pgen = generator(
    bison,
    output: ['@BASENAME@.tab.c', '@BASENAME@.tab.h'],
    arguments: ['@INPUT@', '--defines=@OUTPUT1@', '--output=@OUTPUT0@'],
  )

  if cc.check_header('fnmatch.h')
    executable(
      'convert-dtsv0',
      [
        lgen.process('convert-dtsv0-lexer.l'),
        'srcpos.c',
      ],
      dependencies: util_dep,
      install: true,
    )
  endif

  executable(
    'dtc',
    [
      lgen.process('dtc-lexer.l'),
      pgen.process('dtc-parser.y'),
      'checks.c',
      'data.c',
      'dtc.c',
      'flattree.c',
      'fstree.c',
      'livetree.c',
      'srcpos.c',
      'treesource.c',
      yamltree,
    ],
    dependencies: [util_dep, yaml],
    install: true,
  )

  foreach e: ['fdtdump', 'fdtget', 'fdtput', 'fdtoverlay']
    executable(e, files(e + '.c'), dependencies: util_dep, install: true)
  endforeach

  install_data(
    'dtdiff',
    install_dir: get_option('prefix') / get_option('bindir'),
    install_mode: 'rwxr-xr-x',
  )
endif

if not meson.is_cross_build()
  if py.found() and swig.found()
    subdir('pylibfdt')
  endif

  if get_option('tools')
    subdir('tests')
  endif
endif
