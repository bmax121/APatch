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

int main(int argc, char *argv[])
{
	void *fdt;
	int subnode1_offset, subnode2_offset, subsubnode2_offset;
	int err;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	subnode1_offset = fdt_path_offset(fdt, "/subnode@1");
	if (subnode1_offset < 0)
		FAIL("Couldn't find \"/subnode1\": %s",
		     fdt_strerror(subnode1_offset));
	check_getprop_cell(fdt, subnode1_offset, "prop-int", TEST_VALUE_1);

	subnode2_offset = fdt_path_offset(fdt, "/subnode@2");
	if (subnode2_offset < 0)
		FAIL("Couldn't find \"/subnode2\": %s",
		     fdt_strerror(subnode2_offset));
	check_getprop_cell(fdt, subnode2_offset, "prop-int", TEST_VALUE_2);

	subsubnode2_offset = fdt_path_offset(fdt, "/subnode@2/subsubnode");
	if (subsubnode2_offset < 0)
		FAIL("Couldn't find \"/subnode@2/subsubnode\": %s",
		     fdt_strerror(subsubnode2_offset));
	check_getprop_cell(fdt, subsubnode2_offset, "prop-int", TEST_VALUE_2);

	err = fdt_nop_node(fdt, subnode1_offset);
	if (err)
		FAIL("fdt_nop_node(subnode1): %s", fdt_strerror(err));

	subnode1_offset = fdt_path_offset(fdt, "/subnode@1");
	if (subnode1_offset != -FDT_ERR_NOTFOUND)
		FAIL("fdt_path_offset(subnode1) returned \"%s\" instead of \"%s\"",
		     fdt_strerror(subnode1_offset),
		     fdt_strerror(-FDT_ERR_NOTFOUND));

	subnode2_offset = fdt_path_offset(fdt, "/subnode@2");
	if (subnode2_offset < 0)
		FAIL("Couldn't find \"/subnode2\": %s",
		     fdt_strerror(subnode2_offset));
	check_getprop_cell(fdt, subnode2_offset, "prop-int", TEST_VALUE_2);

	subsubnode2_offset = fdt_path_offset(fdt, "/subnode@2/subsubnode");
	if (subsubnode2_offset < 0)
		FAIL("Couldn't find \"/subnode@2/subsubnode\": %s",
		     fdt_strerror(subsubnode2_offset));
	check_getprop_cell(fdt, subsubnode2_offset, "prop-int", TEST_VALUE_2);

	err = fdt_nop_node(fdt, subnode2_offset);
	if (err)
		FAIL("fdt_nop_node(subnode2): %s", fdt_strerror(err));

	subnode1_offset = fdt_path_offset(fdt, "/subnode@1");
	if (subnode1_offset != -FDT_ERR_NOTFOUND)
		FAIL("fdt_path_offset(subnode1) returned \"%s\" instead of \"%s\"",
		     fdt_strerror(subnode1_offset),
		     fdt_strerror(-FDT_ERR_NOTFOUND));

	subnode2_offset = fdt_path_offset(fdt, "/subnode@2");
	if (subnode2_offset != -FDT_ERR_NOTFOUND)
		FAIL("fdt_path_offset(subnode2) returned \"%s\" instead of \"%s\"",
		     fdt_strerror(subnode2_offset),
		     fdt_strerror(-FDT_ERR_NOTFOUND));

	subsubnode2_offset = fdt_path_offset(fdt, "/subnode@2/subsubnode");
	if (subsubnode2_offset != -FDT_ERR_NOTFOUND)
		FAIL("fdt_path_offset(subsubnode2) returned \"%s\" instead of \"%s\"",
		     fdt_strerror(subsubnode2_offset),
		     fdt_strerror(-FDT_ERR_NOTFOUND));

	PASS();
}
