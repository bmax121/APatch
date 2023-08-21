#include <base.hpp>

#include "cpio.hpp"
#include "boot.hpp"
#include "compress.hpp"
#include <unistd.h>

using namespace std;

bool check_env(const char *name) {
    const char *val = getenv(name);
    return val != nullptr && val == "true"sv;
}

#define for_each_line(line, buf, size) \
for (char *line = (char *) buf; line < (char *) buf + size && line[0]; line = strchr(line + 1, '\n') + 1)

#define for_each_str(str, buf, size) \
for (char *str = (char *) buf; str < (char *) buf + size; str += strlen(str) + 1)


int cpio_commands(int argc, char *argv[]) {
    char *incpio = argv[0];
    ++argv;
    --argc;

    cpio cpio;
    if (access(incpio, R_OK) == 0)
        cpio.load_cpio(incpio);

    int cmdc;
    char *cmdv[6];

    for (int i = 0; i < argc; ++i) {
        // Reset
        cmdc = 0;
        memset(cmdv, 0, sizeof(cmdv));

        // Split the commands
        char *tok = strtok(argv[i], " ");
        while (tok && cmdc < std::size(cmdv)) {
            if (cmdc == 0 && tok[0] == '#')
                break;
            cmdv[cmdc++] = tok;
            tok = strtok(nullptr, " ");
        }

        if (cmdc == 0)
            continue;

        if (cmdc == 2 && cmdv[0] == "exists"sv) {
            exit(!cpio.exists(cmdv[1]));
        } else if (cmdc >= 2 && cmdv[0] == "rm"sv) {
            bool r = cmdc > 2 && cmdv[1] == "-r"sv;
            cpio.rm(cmdv[1 + r], r);
        } else if (cmdc == 3 && cmdv[0] == "mv"sv) {
            cpio.mv(cmdv[1], cmdv[2]);
        } else if (cmdv[0] == "extract"sv) {
            if (cmdc == 3) {
                return !cpio.extract(cmdv[1], cmdv[2]);
            } else {
                cpio.extract();
                return 0;
            }
        } else if (cmdc == 3 && cmdv[0] == "mkdir"sv) {
            cpio.mkdir(strtoul(cmdv[1], nullptr, 8), cmdv[2]);
        } else if (cmdc == 3 && cmdv[0] == "ln"sv) {
            cpio.ln(cmdv[1], cmdv[2]);
        } else if (cmdc == 4 && cmdv[0] == "add"sv) {
            cpio.add(strtoul(cmdv[1], nullptr, 8), cmdv[2], cmdv[3]);
        } else {
            return 1;
        }
    }

    cpio.dump(incpio);
    return 0;
}
