# This process is launched by app_process from a shell command, so R8 cannot
# discover its entry point through normal Android reachability analysis.
-keep class moe.n4tsu.dextop.privilege.** { *; }
-keep class moe.n4tsu.dextop.input.PrivilegedInputService { *; }
-keep class moe.n4tsu.dextop.input.IPrivilegedInputService$Stub { *; }
-keep class moe.n4tsu.dextop.input.IPrivilegedInputCallback$Stub { *; }
-keep class moe.n4tsu.dextop.linux.LinuxContainerService { *; }
-keep class moe.n4tsu.dextop.linux.LinuxServiceHost { *; }
-keep class moe.n4tsu.dextop.linux.ILinuxContainerService$Stub { *; }
-keep class moe.n4tsu.dextop.linux.ILinuxContainerCallback$Stub { *; }
