hook_impl_files = files(['hook_impl.cpp', 'hook_impl.h', 'hook_impl_params.h'])
hook_impl_inc = include_directories('../../include')


main_hook_files = files(['main_hook.c'])

file_redirect_hook_files = files(['file_redirect_hook.c'])

gsl_alloc_hook_files = files(['gsl_alloc_hook.c'])

hook_impl_lib = shared_library(
    'hook_impl',
    hook_impl_files,
    c_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    cpp_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    dependencies: ([linkernsbypass_dep, liblog_dep]),
    include_directories: ([hook_impl_inc]),
    install: true
)

hook_impl_dep = declare_dependency(
    link_args: (['-z', 'global']),
    link_with: ([hook_impl_lib]),
    include_directories: hook_impl_inc,
)

main_hook_lib = shared_library(
    'main_hook',
    main_hook_files,
    c_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    cpp_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    dependencies: ([hook_impl_dep]),
    install: true
)

file_redirect_hook_lib = shared_library(
    'file_redirect_hook',
    file_redirect_hook_files,
    c_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    cpp_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    dependencies: ([hook_impl_dep]),
    install: true
)

gsl_alloc_hook_lib = shared_library(
    'gsl_alloc_hook',
    gsl_alloc_hook_files,
    c_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    cpp_args: (['-fvisibility=hidden',  '-Wall', '-Wextra']),
    dependencies: ([hook_impl_dep]),
    install: true
)