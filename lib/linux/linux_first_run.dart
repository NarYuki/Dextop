/// Keys of the first Linux setup, which runs inside the regular setup flow (LinuxSetupPage.firstRun).
abstract final class LinuxFirstRun {
  /// Set once the first setup was applied or skipped; cleared by resetting Linux.
  static const doneKey = 'linux_first_run_done';

  /// Store sources to prepare as soon as the first container is installed.
  static const prepareKey = 'linux_first_run_prepare';
}
