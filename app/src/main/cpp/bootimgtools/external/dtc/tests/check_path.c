// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for node existence
 * Copyright (C) 2016 Konsulko Inc.
 */

#include <stdio.h>

#include <libfdt.h>

#include "tests.h"

#define CHECK(code) \
	{ \
		int err = (code); \
		if (err) \
			FAIL(#code ": %s", fdt_strerror(err)); \
	}

/* 4k ought to be enough for anybody */
#define FDT_COPY_SIZE	(4 * 1024)

static void *open_dt(char *path)
{
	void *dt, *copy;

	dt = load_blob(path);
	copy = xmalloc(FDT_COPY_SIZE);

	/*
	 * Resize our DTs to 4k so that we have room to operate on
	 */
	CHECK(fdt_open_into(dt, copy, FDT_COPY_SIZE));

	return copy;
}

int main(int argc, char *argv[])
{
	void *fdt_base;
	int fail_config, exists, check_exists;

	test_init(argc, argv);
	fail_config = 0;

	if (argc != 4)
		fail_config = 1;

	if (!fail_config) {
		if (!strcmp(argv[2], "exists"))
			check_exists = 1;
		else if (!strcmp(argv[2], "not-exists"))
			check_exists = 0;
		else
			fail_config = 1;
	}

	if (fail_config)
		CONFIG("Usage: %s <base dtb> <[exists|not-exists]> <node-path>", argv[0]);

	fdt_base = open_dt(argv[1]);

	exists = fdt_path_offset(fdt_base, argv[3]) >= 0;

	if (exists == check_exists)
		PASS();
	else
		FAIL();
}
