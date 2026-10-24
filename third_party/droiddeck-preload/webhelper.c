#define _GNU_SOURCE
#include <stdlib.h>
#include <string.h>

#ifndef WEBHELPER_DEFAULT
#define WEBHELPER_DEFAULT "--no-zygote --in-process-gpu -AudioServiceOutOfProcess"
#endif

static int is_webhelper_browser(const char *path, char *const argv[]) {
  if (!path || !argv) return 0;
  const char *base = strrchr(path, '/');
  base = base ? base + 1 : path;
  if (strcmp(base, "steamwebhelper")) return 0;
  for (int i = 1; argv[i]; i++)
    if (!strncmp(argv[i], "--type=", 7)) return 0;
  return 1;
}

static char *join_features(const char *have, const char *add) {
  size_t a = have ? strlen(have) : 0, b = strlen(add);
  char *out = malloc(a + b + 2);
  if (!out) return NULL;
  out[0] = 0;
  if (a) strcat(strcat(out, have), ",");
  return strcat(out, add);
}

__attribute__((visibility("hidden"))) char *const *bl_webhelper_argv(const char *path, char *const argv[]) {
  if (!is_webhelper_browser(path, argv)) return argv;
  const char *spec = getenv("BL_WEBHELPER_ARGS");
  if (!spec || !*spec) spec = WEBHELPER_DEFAULT;
  if (!strcmp(spec, "stock")) return argv;

  int argc = 0;
  while (argv[argc]) argc++;
  char *tokens = strdup(spec);
  if (!tokens) return argv;
  int extra = 0;
  for (char *c = tokens; *c; c++)
    if (*c == ' ') extra++;
  char **out = calloc(argc + extra + 4, sizeof *out);
  if (!out) return argv;

  const char *enable = NULL, *disable = NULL;
  int n = 0;
  for (int i = 0; i < argc; i++) {
    if (i && !strncmp(argv[i], "--enable-features=", 18)) enable = argv[i] + 18;
    else if (i && !strncmp(argv[i], "--disable-features=", 19)) disable = argv[i] + 19;
    else out[n++] = argv[i];
  }
  char *add_enable = NULL, *add_disable = NULL, *save = NULL;
  for (char *t = strtok_r(tokens, " ", &save); t; t = strtok_r(NULL, " ", &save)) {
    if (t[0] == '+' && t[1]) {
      char *joined = join_features(add_enable, t + 1);
      free(add_enable);
      add_enable = joined;
    } else if (t[0] == '-' && t[1] && t[1] != '-') {
      char *joined = join_features(add_disable, t + 1);
      free(add_disable);
      add_disable = joined;
    } else if (!strncmp(t, "--", 2)) {
      out[n++] = t;
    }
  }
  char *merged;
  if (add_enable && (merged = join_features(enable, add_enable))) enable = merged;
  if (add_disable && (merged = join_features(disable, add_disable))) disable = merged;
  char *flag;
  if (enable && (flag = malloc(strlen(enable) + 19))) out[n++] = strcat(strcpy(flag, "--enable-features="), enable);
  if (disable && (flag = malloc(strlen(disable) + 20))) out[n++] = strcat(strcpy(flag, "--disable-features="), disable);
  out[n] = NULL;
  return out;
}
