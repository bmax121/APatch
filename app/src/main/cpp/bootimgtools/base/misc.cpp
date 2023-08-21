#include <sys/types.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <pwd.h>
#include <unistd.h>
#include <random>
#include <string>

#include <base.hpp>

using namespace std;

int fork_dont_care() {
    if (int pid = xfork()) {
        waitpid(pid, nullptr, 0);
        return pid;
    } else if (xfork()) {
        exit(0);
    }
    return 0;
}

int exec_command_sync(exec_t &exec) {
    int pid = exec_command(exec);
    if (pid < 0)
        return -1;
    int status;
    waitpid(pid, &status, 0);
    return WEXITSTATUS(status);
}


static char *argv0;
static size_t name_len;
void init_argv0(int argc, char **argv) {
    argv0 = argv[0];
    name_len = (argv[argc - 1] - argv[0]) + strlen(argv[argc - 1]) + 1;
}

/*
 * Bionic's atoi runs through strtol().
 * Use our own implementation for faster conversion.
 */
int parse_int(string_view s) {
    int val = 0;
    for (char c : s) {
        if (!c) break;
        if (c > '9' || c < '0')
            return -1;
        val = val * 10 + c - '0';
    }
    return val;
}

uint32_t binary_gcd(uint32_t u, uint32_t v) {
    if (u == 0) return v;
    if (v == 0) return u;
    auto shift = __builtin_ctz(u | v);
    u >>= __builtin_ctz(u);
    do {
        v >>= __builtin_ctz(v);
        if (u > v) {
            auto t = v;
            v = u;
            u = t;
        }
        v -= u;
    } while (v != 0);
    return u << shift;
}

int switch_mnt_ns(int pid) {
    char mnt[32];
    snprintf(mnt, sizeof(mnt), "/proc/%d/ns/mnt", pid);
    if (access(mnt, R_OK) == -1) return 1; // Maybe process died..

    int fd, ret;
    fd = xopen(mnt, O_RDONLY);
    if (fd < 0) return 1;
    // Switch to its namespace
    ret = xsetns(fd, 0);
    close(fd);
    return ret;
}

string &replace_all(string &str, string_view from, string_view to) {
    size_t pos = 0;
    while((pos = str.find(from, pos)) != string::npos) {
        str.replace(pos, from.length(), to);
        pos += to.length();
    }
    return str;
}

template <class T>
static auto split_impl(T s, T delims) {
    vector<std::decay_t<T>> result;
    size_t base = 0;
    size_t found;
    while (true) {
        found = s.find_first_of(delims, base);
        result.push_back(s.substr(base, found - base));
        if (found == string::npos)
            break;
        base = found + 1;
    }
    return result;
}

vector<string> split(const string &s, const string &delims) {
    return split_impl<const string&>(s, delims);
}

vector<string_view> split_ro(string_view s, string_view delims) {
    return split_impl<string_view>(s, delims);
}
