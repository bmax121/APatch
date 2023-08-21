// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_node_offset_by_phandle()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdarg.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_search(void *fdt, uint32_t phandle, int target)
{
	int offset;

	offset = fdt_node_offset_by_phandle(fdt, phandle);

	if (offset != target)
		FAIL("fdt_node_offset_by_phandle(0x%x) returns %d "
		     "instead of %d", phandle, offset, target);
}

int main(int argc, char *argv[])
{
	void *fdt;
	int subnode2_offset, subsubnode2_offset;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	subnode2_offset = fdt_path_offset(fdt, "/subnode@2");
	subsubnode2_offset = fdt_path_offset(fdt, "/subnode@2/subsubnode@0");

	if ((subnode2_offset < 0) || (subsubnode2_offset < 0))
		FAIL("Can't find required nodes");

	check_search(fdt, PHANDLE_1, subnode2_offset);
	check_search(fdt, PHANDLE_2, subsubnode2_offset);
	check_search(fdt, ~PHANDLE_1, -FDT_ERR_NOTFOUND);
	check_search(fdt, 0, -FDT_ERR_BADPHANDLE);
	check_search(fdt, -1, -FDT_ERR_BADPHANDLE);

	PASS();
}
