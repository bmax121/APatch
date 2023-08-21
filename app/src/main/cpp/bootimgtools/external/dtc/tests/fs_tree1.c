// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase/tool constructing an fs tree for further test
 * Copyright (C) 2018 David Gibson, Red Hat Inc.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <stdint.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void start_dir(const char *name)
{
	int rc;

	rc = mkdir(name, 0777);
	if (rc != 0)
		FAIL("mkdir(\"%s\"): %s", name, strerror(errno));

	rc = chdir(name);
	if (rc != 0)
		FAIL("chdir(\"%s\"): %s", name, strerror(errno));
}

static void end_dir(void)
{
	int rc;

	rc = chdir("..");
	if (rc != 0)
		FAIL("chdir(..): %s", strerror(errno));
}
	
static void mkfile(const char *name, void *data, size_t len)
{
	int fd;
	int rc;

	fd = open(name, O_WRONLY|O_CREAT, 0666);
	if (fd < 0)
		FAIL("open(\"%s\"): %s", name, strerror(errno));

	rc = write(fd, data, len);
	if (rc < 0)
		FAIL("write(\"%s\"): %s", name, strerror(errno));
	if ((unsigned)rc != len)
		FAIL("write(\"%s\"): short write", name);
	
	rc = close(fd);
	if (rc != 0)
		FAIL("close(\"%s\"): %s", name, strerror(errno));
}

#define mkfile_str(name, s)			  \
	do {					  \
		char str[] = s;			  \
		mkfile((name), str, sizeof(str)); \
	} while (0)

static void mkfile_u32(const char *name, uint32_t val)
{
	val = cpu_to_fdt32(val);
	mkfile(name, &val, sizeof(val));
}

static void mkfile_u64(const char *name, uint64_t val)
{
	val = cpu_to_fdt64(val);
	mkfile(name, &val, sizeof(val));
}

int main(int argc, char *argv[])
{
	const char *base;

	test_init(argc, argv);
	if (argc != 2)
		CONFIG("Usage: %s <path>", argv[0]);

	base = argv[1];

	start_dir(base);
	mkfile_str("compatible", "test_tree1");
	mkfile_u32("prop-int", TEST_VALUE_1);
	mkfile_u64("prop-int64", 0xdeadbeef01abcdefULL);
	mkfile_str("prop-str", "hello world");
	mkfile_u32("#address-cells", 1);
	mkfile_u32("#size-cells", 0);

	{
		start_dir("subnode@1");

		mkfile_str("compatible", "subnode1");
		mkfile_u32("reg", 1);
		mkfile_u32("prop-int", TEST_VALUE_1);

		{
			start_dir("subsubnode");

			mkfile_str("compatible", "subsubnode1\0subsubnode");
			mkfile_str("placeholder", "this is a placeholder string\0string2");
			mkfile_u32("prop-int", TEST_VALUE_1);
			
			end_dir();
		}

		{
			start_dir("ss1");
			end_dir();
		}
		
		end_dir();
	}

	{
		start_dir("subnode@2");

		mkfile_u32("reg", 2);
		mkfile_u32("linux,phandle", 0x2000);
		mkfile_u32("prop-int", TEST_VALUE_2);
		mkfile_u32("#address-cells", 1);
		mkfile_u32("#size-cells", 0);

		{
			start_dir("subsubnode@0");

			mkfile_u32("reg", 0);
			mkfile_u32("phandle", 0x2001);
			mkfile_str("compatible", "subsubnode2\0subsubnode");
			mkfile_u32("prop-int", TEST_VALUE_2);
			
			end_dir();
		}

		{
			start_dir("ss2");
			end_dir();
		}
		
		end_dir();
	}

	PASS();
}
