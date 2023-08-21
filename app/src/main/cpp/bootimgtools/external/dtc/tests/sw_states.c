// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for error handling with sequential write states
 * Copyright (C) 2018 David Gibson, Red Hat Inc.
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

#define CHECK_OK(code)							\
	do {								\
		verbose_printf(" OK: %s\n", #code);			\
		err = (code);						\
		if (err)						\
			FAIL(#code ": %s", fdt_strerror(err));		\
	} while (0)

#define CHECK_BADSTATE(code)						\
	do {								\
		verbose_printf("BAD: %s\n", #code);			\
		err = (code);						\
		if (err == 0)						\
			FAIL(#code ": succeeded in bad state");		\
		else if (err != -FDT_ERR_BADSTATE)			\
			FAIL(#code ": %s", fdt_strerror(err));		\
	} while (0)

int main(int argc, char *argv[])
{
	void *fdt = NULL;
	int err;

	test_init(argc, argv);

	fdt = xmalloc(SPACE);

	err = fdt_create(fdt, SPACE);
	if (err)
		FAIL("fdt_create(): %s", fdt_strerror(err));

	/* Memory reserve state */

	CHECK_BADSTATE(fdt_begin_node(fdt, ""));
	CHECK_BADSTATE(fdt_property_string(fdt, "bad-str", "TEST_STRING_1"));
	CHECK_BADSTATE(fdt_end_node(fdt));
	CHECK_BADSTATE(fdt_finish(fdt));

	CHECK_OK(fdt_add_reservemap_entry(fdt, TEST_ADDR_1, TEST_SIZE_1));
	CHECK_OK(fdt_add_reservemap_entry(fdt, TEST_ADDR_2, TEST_SIZE_2));

	CHECK_BADSTATE(fdt_begin_node(fdt, ""));
	CHECK_BADSTATE(fdt_property_string(fdt, "bad-str", "TEST_STRING_1"));
	CHECK_BADSTATE(fdt_end_node(fdt));
	CHECK_BADSTATE(fdt_finish(fdt));

	CHECK_OK(fdt_finish_reservemap(fdt));

	/* Structure state */

	CHECK_BADSTATE(fdt_add_reservemap_entry(fdt, TEST_ADDR_1, TEST_SIZE_1));
	CHECK_BADSTATE(fdt_finish_reservemap(fdt));

	CHECK_OK(fdt_begin_node(fdt, ""));
	CHECK_OK(fdt_property_string(fdt, "compatible", "test_tree1"));
	CHECK_OK(fdt_property_u32(fdt, "prop-int", TEST_VALUE_1));
	CHECK_OK(fdt_property_u64(fdt, "prop-int64", TEST_VALUE64_1));
	CHECK_OK(fdt_property_string(fdt, "prop-str", TEST_STRING_1));
	CHECK_OK(fdt_property_u32(fdt, "#address-cells", 1));
	CHECK_OK(fdt_property_u32(fdt, "#size-cells", 0));

	CHECK_OK(fdt_begin_node(fdt, "subnode@1"));
	CHECK_OK(fdt_property_string(fdt, "compatible", "subnode1"));
	CHECK_OK(fdt_property_u32(fdt, "reg", 1));
	CHECK_OK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_1));
	CHECK_OK(fdt_begin_node(fdt, "subsubnode"));
	CHECK_OK(fdt_property(fdt, "compatible", "subsubnode1\0subsubnode",
			   23));
	CHECK_OK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_1));
	CHECK_OK(fdt_end_node(fdt));
	CHECK_OK(fdt_begin_node(fdt, "ss1"));
	CHECK_OK(fdt_end_node(fdt));
	CHECK_OK(fdt_end_node(fdt));

	CHECK_OK(fdt_begin_node(fdt, "subnode@2"));
	CHECK_OK(fdt_property_u32(fdt, "reg", 2));
	CHECK_OK(fdt_property_cell(fdt, "linux,phandle", PHANDLE_1));
	CHECK_OK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_2));
	CHECK_OK(fdt_property_u32(fdt, "#address-cells", 1));
	CHECK_OK(fdt_property_u32(fdt, "#size-cells", 0));
	CHECK_OK(fdt_begin_node(fdt, "subsubnode@0"));
	CHECK_OK(fdt_property_u32(fdt, "reg", 0));
	CHECK_OK(fdt_property_cell(fdt, "phandle", PHANDLE_2));
	CHECK_OK(fdt_property(fdt, "compatible", "subsubnode2\0subsubnode",
			   23));
	CHECK_OK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_2));
	CHECK_OK(fdt_end_node(fdt));
	CHECK_OK(fdt_begin_node(fdt, "ss2"));
	CHECK_OK(fdt_end_node(fdt));

	CHECK_OK(fdt_end_node(fdt));

	CHECK_OK(fdt_end_node(fdt));

	CHECK_OK(fdt_finish(fdt));

	/* Completed state */

	CHECK_BADSTATE(fdt_add_reservemap_entry(fdt, TEST_ADDR_1, TEST_SIZE_1));
	CHECK_BADSTATE(fdt_finish_reservemap(fdt));
	CHECK_BADSTATE(fdt_begin_node(fdt, ""));
	CHECK_BADSTATE(fdt_property_string(fdt, "bad-str", "TEST_STRING_1"));
	CHECK_BADSTATE(fdt_end_node(fdt));
	CHECK_BADSTATE(fdt_finish(fdt));

	PASS();
}
