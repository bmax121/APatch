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

/* This is not derived programmatically. May require adjustment to changes. */
#define SPACE	285

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
	int offset, s1;
	int strsize1, strsize2;

	/*
	 * Check OOM path, and check that property is cleaned up if it fails
	 * with OOM, rather than adding an orphan name.
	 *
	 * SW OOM is tested with the realloc/resize strategies.
	 */
	test_init(argc, argv);

	fdt = xmalloc(SPACE);

	/* First create empty tree with SW */
	CHECK(fdt_create_empty_tree(fdt, SPACE));

	CHECK(fdt_add_mem_rsv(fdt, TEST_ADDR_1, TEST_SIZE_1));
	CHECK(fdt_add_mem_rsv(fdt, TEST_ADDR_2, TEST_SIZE_2));

	CHECK(fdt_setprop_string(fdt, 0, "compatible", "test_oom"));
	CHECK(fdt_setprop_u32(fdt, 0, "prop-int", TEST_VALUE_1));
	CHECK(fdt_setprop_u64(fdt, 0, "prop-int64", TEST_VALUE64_1));
	CHECK(fdt_setprop_string(fdt, 0, "prop-str", TEST_STRING_1));

	OFF_CHECK(offset, fdt_add_subnode(fdt, 0, "subnode@1"));
	s1 = offset;

	strsize1 = fdt_size_dt_strings(fdt);
	err = fdt_setprop_string(fdt, s1, "unique", "subnode1");
	if (err != -FDT_ERR_NOSPACE)
		FAIL("fdt_setprop_string(fdt, s1, \"compatible\", \"subnode1\"): %s", fdt_strerror(err));
	strsize2 = fdt_size_dt_strings(fdt);

	if (strsize1 != strsize2)
		FAIL("fdt_setprop NOSPACE error failed to clean up allocated string\n");
	err = 0;

	/* Ensure we failed in the right place */
	CHECK(fdt_setprop_string(fdt, s1, "unique", ""));

	CHECK(fdt_pack(fdt));

	PASS();
}
