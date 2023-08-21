// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_appendprop()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

#define SPACE		65536

#define CHECK(code) \
	{ \
		err = (code); \
		if (err) \
			FAIL(#code ": %s", fdt_strerror(err)); \
	}

int main(int argc, char *argv[])
{
	void *fdt;
	int err;
	uint8_t bytes[] = {0x00, 0x01, 0x02, 0x03, 0x04};

	test_init(argc, argv);

	/* Create an empty tree first */
	fdt = xmalloc(SPACE);
	CHECK(fdt_create(fdt, SPACE));
	CHECK(fdt_finish_reservemap(fdt));
	CHECK(fdt_begin_node(fdt, ""));
	CHECK(fdt_end_node(fdt));
	CHECK(fdt_finish(fdt));

	/* Now use appendprop to add properties */
	CHECK(fdt_open_into(fdt, fdt, SPACE));

	CHECK(fdt_appendprop(fdt, 0, "prop-bytes", bytes, sizeof(bytes)));
	CHECK(fdt_appendprop_cell(fdt, 0, "prop-int", TEST_VALUE_1));
	CHECK(fdt_appendprop_u64(fdt, 0, "prop-int64", TEST_VALUE64_1));
	CHECK(fdt_appendprop_string(fdt, 0, "prop-str", TEST_STRING_1));

	CHECK(fdt_pack(fdt));

	save_blob("appendprop1.test.dtb", fdt);

	PASS();
}
