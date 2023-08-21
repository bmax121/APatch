// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_nop_node()
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

#define SPACE	65536

#define CHECK(code) \
	{ \
		err = (code); \
		if (err) \
			FAIL(#code ": %s", fdt_strerror(err)); \
	}

#define OFF_CHECK(off, code) \
	{ \
		(off) = (code); \
		if (off < 0) \
			FAIL(#code ": %s", fdt_strerror(off)); \
	}

int main(int argc, char *argv[])
{
	void *fdt;
	int err;
	int offset;

	test_init(argc, argv);

	fdt = xmalloc(SPACE);

	CHECK(fdt_create(fdt, SPACE));

	CHECK(fdt_finish_reservemap(fdt));
	CHECK(fdt_begin_node(fdt, ""));
	CHECK(fdt_property_cell(fdt, "prop1", TEST_VALUE_1));
	CHECK(fdt_property_cell(fdt, "prop2", TEST_VALUE_2));
	CHECK(fdt_end_node(fdt));
	CHECK(fdt_finish(fdt));

	verbose_printf("Built empty tree, totalsize = %d\n",
		       fdt_totalsize(fdt));

	CHECK(fdt_open_into(fdt, fdt, SPACE));

	check_getprop_cell(fdt, 0, "prop1", TEST_VALUE_1);
	check_getprop_cell(fdt, 0, "prop2", TEST_VALUE_2);

	CHECK(fdt_nop_property(fdt, 0, "prop1"));

	check_getprop_cell(fdt, 0, "prop2", TEST_VALUE_2);

	OFF_CHECK(offset, fdt_add_subnode(fdt, 0, "subnode"));

	check_getprop_cell(fdt, 0, "prop2", TEST_VALUE_2);

	PASS();
}
